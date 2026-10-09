package com.fitsync.notification.admin;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.fitsync.notification.admin.AdminEmailDtos.AvailabilityResponse;
import com.fitsync.notification.admin.AdminEmailDtos.KindAvailability;
import com.fitsync.notification.admin.AdminEmailDtos.Outcome;
import com.fitsync.notification.admin.AdminEmailDtos.SendResponse;
import com.fitsync.notification.domain.EmailKind;
import com.fitsync.notification.domain.EmailStatus;
import com.fitsync.notification.domain.EmailTrigger;
import com.fitsync.notification.entity.EmailEntity;
import com.fitsync.notification.entity.GoalEntity;
import com.fitsync.notification.entity.PlanDayEntity;
import com.fitsync.notification.entity.RecipientEntity;
import com.fitsync.notification.entity.WeightEntity;
import com.fitsync.notification.handler.BodyMetricsRecordedHandler;
import com.fitsync.notification.repository.EmailRepository;
import com.fitsync.notification.repository.GoalRepository;
import com.fitsync.notification.repository.PlanDayRepository;
import com.fitsync.notification.repository.RecipientRepository;
import com.fitsync.notification.repository.WeightRepository;
import com.fitsync.notification.service.EmailComposer;
import com.fitsync.notification.service.EmailComposer.ComposedEmail;
import com.fitsync.notification.service.EmailQueue;
import com.fitsync.notification.service.RecipientTime;
import com.fitsync.web.error.ApiException;
import com.fitsync.web.error.ConflictException;

/**
 * An admin's "send now" for E1, E4 and E5. The email is composed from what this service already
 * holds for the user and refused with a reason when that is not enough, because an email must
 * never claim something the data does not show. The user's opt-in still applies; quiet hours and
 * the once-per-day or once-per-week key do not. The email goes through the queue and is sent as
 * soon as the queueing transaction commits, so the answer can say whether SES took it.
 *
 * <p>Every request is logged with the admin, the user, the kind and the outcome, and an admin may
 * send at most {@link #HOURLY_LIMIT} emails an hour. The answer never carries an address or a body.
 */
@Service
public class AdminEmailService {

    static final int HOURLY_LIMIT = 10;
    private static final String REST = "REST";
    private static final String EMAIL_CHANNEL = "EMAIL";

    private static final Logger log = LoggerFactory.getLogger(AdminEmailService.class);

    private final RecipientRepository recipients;
    private final PlanDayRepository planDays;
    private final GoalRepository goals;
    private final WeightRepository weights;
    private final EmailRepository emails;
    private final EmailQueue queue;
    private final EmailComposer composer;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public AdminEmailService(RecipientRepository recipients, PlanDayRepository planDays, GoalRepository goals,
                             WeightRepository weights, EmailRepository emails, EmailQueue queue,
                             EmailComposer composer, TransactionTemplate transactions, Clock clock) {
        this.recipients = recipients;
        this.planDays = planDays;
        this.goals = goals;
        this.weights = weights;
        this.emails = emails;
        this.queue = queue;
        this.composer = composer;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** Which kinds can be sent to the user now, and why not for the others. */
    public AvailabilityResponse availability(UUID userId) {
        List<KindAvailability> kinds = transactions.execute(status -> {
            RecipientEntity recipient = recipientOrNotFound(userId);
            Instant now = clock.instant();
            return Arrays.stream(AdminEmailKind.values())
                    .map(kind -> KindAvailability.of(kind, compose(kind, recipient, now).refusal()))
                    .toList();
        });
        return new AvailabilityResponse(kinds == null ? List.of() : kinds);
    }

    /** Composes and queues the email, and reports what happened when it was sent. */
    public SendResponse send(UUID adminId, UUID userId, AdminEmailKind kind) {
        UUID emailId;
        try {
            // The email is sent when this transaction commits, before execute returns.
            emailId = transactions.execute(status -> queueChecked(adminId, userId, kind));
        } catch (ApiException ex) {
            log.info("event=admin_email_send adminId={} userId={} kind={} outcome=REFUSED reason={}",
                    sanitizeForLog(adminId), sanitizeForLog(userId), sanitizeForLog(kind),
                    sanitizeForLog(ex.getCode()));
            throw ex;
        }
        Outcome outcome = emailId == null ? Outcome.DROPPED
                : emails.findById(emailId).map(AdminEmailService::outcomeOf).orElse(Outcome.DROPPED);
        log.info("event=admin_email_send adminId={} userId={} kind={} outcome={} emailId={}",
                sanitizeForLog(adminId), sanitizeForLog(userId), sanitizeForLog(kind), sanitizeForLog(outcome),
                sanitizeForLog(emailId));
        return new SendResponse(kind, outcome);
    }

    private UUID queueChecked(UUID adminId, UUID userId, AdminEmailKind kind) {
        Instant now = clock.instant();
        long recent = emails.countByTriggerAndAdminIdAndCreatedAtGreaterThanEqual(EmailTrigger.ADMIN, adminId,
                now.minus(Duration.ofHours(1)));
        if (recent >= HOURLY_LIMIT) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "ADMIN_EMAIL_LIMIT",
                    "An admin can send " + HOURLY_LIMIT + " emails an hour. Try again later.");
        }
        RecipientEntity recipient = recipientOrNotFound(userId);
        Composed composed = compose(kind, recipient, now);
        if (composed.refusal() != null) {
            throw new ConflictException(composed.refusal().name(), composed.refusal().message(), Map.of());
        }
        return queue.queueForAdmin(userId, kind.emailKind(), composed.email(), adminId);
    }

    private RecipientEntity recipientOrNotFound(UUID userId) {
        return recipients.findById(userId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "This user is not known to notifications"));
    }

    private Composed compose(AdminEmailKind kind, RecipientEntity recipient, Instant now) {
        if (recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            return Composed.refused(AdminEmailRefusal.NO_ADDRESS);
        }
        if (recipient.getDeletionScheduledFor() != null) {
            return Composed.refused(AdminEmailRefusal.DELETION_PENDING);
        }
        if (!recipient.isEmailEnabled()) {
            return Composed.refused(AdminEmailRefusal.EMAIL_OFF);
        }
        ZoneId zone = RecipientTime.zoneOf(recipient);
        LocalDate today = now.atZone(zone).toLocalDate();
        return switch (kind) {
            case TRAINING_REMINDER -> trainingReminder(recipient, today, zone);
            case GOAL_REACHED -> goalReached(recipient, zone);
            case WEEKLY_REPORT -> weeklyReport(recipient, today);
        };
    }

    /** Only today's planned session, so the subject "Today: ..." stays true. */
    private Composed trainingReminder(RecipientEntity recipient, LocalDate today, ZoneId zone) {
        if (!recipient.isWorkoutReminderEnabled()) {
            return Composed.refused(AdminEmailRefusal.WORKOUT_REMINDERS_OFF);
        }
        PlanDayEntity day = planDays.findByUserIdAndDayOfWeek(recipient.getUserId(), today.getDayOfWeek().getValue())
                .orElse(null);
        if (day == null || REST.equals(day.getKind())) {
            return Composed.refused(AdminEmailRefusal.NO_SESSION_TODAY);
        }
        if (RecipientTime.workedOutOn(recipient, today, zone)) {
            return Composed.refused(AdminEmailRefusal.WORKOUT_LOGGED_TODAY);
        }
        return Composed.of(composer.trainingToday(recipient, day, recipient.getPreferredWorkoutTime()));
    }

    /** Only when the latest weight has reached the active weight goal, as E4 itself requires. */
    private Composed goalReached(RecipientEntity recipient, ZoneId zone) {
        if (!recipient.isGoalProgressEnabled()) {
            return Composed.refused(AdminEmailRefusal.GOAL_PROGRESS_OFF);
        }
        GoalEntity goal = goals.findFirstByUserIdAndGoalTypeAndStatusOrderByUpdatedAtDesc(recipient.getUserId(),
                BodyMetricsRecordedHandler.WEIGHT, BodyMetricsRecordedHandler.ACTIVE).orElse(null);
        if (goal == null || goal.getTargetValue() == null) {
            return Composed.refused(AdminEmailRefusal.NO_WEIGHT_GOAL);
        }
        WeightEntity latest = weights.findFirstByUserIdOrderByMeasuredAtDesc(recipient.getUserId()).orElse(null);
        if (latest == null) {
            return Composed.refused(AdminEmailRefusal.NO_WEIGHT);
        }
        BigDecimal start = goal.getStartWeightKg();
        if (!BodyMetricsRecordedHandler.reached(start, goal.getTargetValue(), latest.getWeightKg())) {
            return Composed.refused(AdminEmailRefusal.GOAL_NOT_REACHED);
        }
        long days = Math.max(0, ChronoUnit.DAYS.between(goal.getStartedAt().atZone(zone).toLocalDate(),
                latest.getMeasuredAt().atZone(zone).toLocalDate()));
        return Composed.of(composer.goalReached(recipient, goal.getTargetValue(), start, latest.getWeightKg(), days));
    }

    /**
     * Last week's report as it was composed from analytics' recap. This service keeps no recap of its
     * own, so the report queued for that week is the record; without one there is nothing true to send.
     */
    private Composed weeklyReport(RecipientEntity recipient, LocalDate today) {
        if (!recipient.isWeeklyReportEnabled()) {
            return Composed.refused(AdminEmailRefusal.WEEKLY_REPORT_OFF);
        }
        String channel = recipient.getWeeklyReportChannel();
        if (channel != null && !EMAIL_CHANNEL.equals(channel)) {
            return Composed.refused(AdminEmailRefusal.WEEKLY_REPORT_NOT_EMAIL);
        }
        LocalDate lastWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
        return emails.findByUserIdAndKindAndDedupeKey(recipient.getUserId(), EmailKind.WEEKLY_REPORT,
                        lastWeekStart.toString())
                .map(report -> Composed.of(new ComposedEmail(report.getSubject(), report.getBodyText())))
                .orElseGet(() -> Composed.refused(AdminEmailRefusal.NO_RECAP));
    }

    /** The values are ids and enum names, but the line break check is cheap and keeps the log honest. */
    private static String sanitizeForLog(Object value) {
        return String.valueOf(value)
                .replace("\r", "_")
                .replace("\n", "_");
    }

    private static Outcome outcomeOf(EmailEntity email) {
        if (email.getStatus() == EmailStatus.SENT) {
            return Outcome.SENT;
        }
        return email.getStatus() == EmailStatus.PENDING ? Outcome.RETRYING : Outcome.DROPPED;
    }

    /** Either an email ready to queue or the reason there is none. */
    private record Composed(ComposedEmail email, AdminEmailRefusal refusal) {

        static Composed of(ComposedEmail email) {
            return new Composed(email, null);
        }

        static Composed refused(AdminEmailRefusal refusal) {
            return new Composed(null, refusal);
        }
    }
}
