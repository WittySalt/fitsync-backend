package com.fitsync.notification.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.fitsync.notification.domain.EmailKind;
import com.fitsync.notification.domain.EmailStatus;
import com.fitsync.notification.email.EmailSender;
import com.fitsync.notification.email.OutgoingEmail;
import com.fitsync.notification.entity.EmailEntity;
import com.fitsync.notification.entity.RecipientEntity;
import com.fitsync.notification.repository.EmailRepository;
import com.fitsync.notification.repository.RecipientRepository;

/**
 * Sends every email whose time has come. Each one is checked again against the settings as they
 * are now, because a switch can be turned off or a workout logged after the email was queued.
 * Quiet hours hold an email until they end. Each email runs in its own transaction, so one failure
 * leaves the rest untouched. Only ids and kinds are logged, never an address or a body.
 */
@Service
public class EmailDispatcher {

    static final int MAX_ATTEMPTS = 5;
    private static final int BATCH = 50;
    private static final int MAX_BATCHES = 20;
    private static final Duration FIRST_RETRY = Duration.ofMinutes(1);
    private static final String EMAIL_CHANNEL = "EMAIL";

    private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);

    private final EmailRepository emails;
    private final RecipientRepository recipients;
    private final EmailSender sender;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public EmailDispatcher(EmailRepository emails, RecipientRepository recipients, EmailSender sender,
                           TransactionTemplate transactions, Clock clock) {
        this.emails = emails;
        this.recipients = recipients;
        this.sender = sender;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** Works through the due emails in batches; every email handled moves out of the due set. */
    public void dispatchDue() {
        for (int round = 0; round < MAX_BATCHES; round++) {
            Instant now = clock.instant();
            List<UUID> due = emails.findTop50ByStatusAndDueAtLessThanEqualOrderByDueAtAsc(EmailStatus.PENDING, now)
                    .stream().map(EmailEntity::getEmailId).toList();
            for (UUID emailId : due) {
                transactions.executeWithoutResult(status -> dispatchOne(emailId));
            }
            if (due.size() < BATCH) {
                return;
            }
        }
    }

    private void dispatchOne(UUID emailId) {
        Instant now = clock.instant();
        EmailEntity email = emails.findById(emailId).orElse(null);
        if (email == null || email.getStatus() != EmailStatus.PENDING || email.getDueAt().isAfter(now)) {
            return;
        }
        if (email.getExpiresAt() != null && !now.isBefore(email.getExpiresAt())) {
            drop(email, "expired");
            return;
        }
        RecipientEntity recipient = recipients.findById(email.getUserId()).orElse(null);
        if (recipient == null || recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            drop(email, "no_address");
            return;
        }
        ZoneId zone = RecipientTime.zoneOf(recipient);
        ZonedDateTime localNow = now.atZone(zone);
        if (email.getKind() != EmailKind.DELETION_SCHEDULED) {
            String reason = unwantedReason(email.getKind(), recipient, localNow);
            if (reason != null) {
                drop(email, reason);
                return;
            }
            if (RecipientTime.inQuietHours(localNow.toLocalTime(), recipient.getQuietHoursStart(),
                    recipient.getQuietHoursEnd())) {
                deferPastQuietHours(email, localNow, recipient);
                return;
            }
        }
        send(email, recipient, now);
    }

    /** Why an email other than E6 is no longer wanted, or null when it still is. */
    private static String unwantedReason(EmailKind kind, RecipientEntity recipient, ZonedDateTime localNow) {
        if (recipient.getDeletionScheduledFor() != null) {
            return "deletion_pending";
        }
        if (!recipient.isEmailEnabled()) {
            return "email_off";
        }
        return switch (kind) {
            case TRAINING_TODAY -> {
                if (!recipient.isWorkoutReminderEnabled()) {
                    yield "toggle_off";
                }
                yield RecipientTime.workedOutOn(recipient, localNow.toLocalDate(), localNow.getZone())
                        ? "workout_logged" : null;
            }
            case GOAL_REACHED -> recipient.isGoalProgressEnabled() ? null : "toggle_off";
            case WEEKLY_REPORT -> {
                if (!recipient.isWeeklyReportEnabled()) {
                    yield "toggle_off";
                }
                // user-service defaults the channel to EMAIL, so an unknown channel counts as EMAIL.
                String channel = recipient.getWeeklyReportChannel();
                yield channel == null || EMAIL_CHANNEL.equals(channel) ? null : "channel_not_email";
            }
            case DELETION_SCHEDULED -> null;
        };
    }

    private void deferPastQuietHours(EmailEntity email, ZonedDateTime localNow, RecipientEntity recipient) {
        Instant quietEnd = RecipientTime.quietHoursEnd(localNow, recipient.getQuietHoursEnd());
        if (email.getExpiresAt() != null && !quietEnd.isBefore(email.getExpiresAt())) {
            drop(email, "quiet_hours_outlast_expiry");
            return;
        }
        email.setDueAt(quietEnd);
        emails.save(email);
        log.info("event=email_deferred emailId={} kind={} reason=quiet_hours", email.getEmailId(), email.getKind());
    }

    private void send(EmailEntity email, RecipientEntity recipient, Instant now) {
        try {
            sender.send(new OutgoingEmail(email.getEmailId(), email.getKind(), recipient.getEmail(),
                    email.getSubject(), email.getBodyText()));
        } catch (RuntimeException ex) {
            int attempts = email.getAttempts() + 1;
            email.setAttempts(attempts);
            if (attempts >= MAX_ATTEMPTS) {
                email.setStatus(EmailStatus.FAILED);
                log.warn("event=email_failed emailId={} kind={} attempts={} exceptionClass={}",
                        email.getEmailId(), email.getKind(), attempts, ex.getClass().getName());
            } else {
                // Doubling waits of 1, 2, 4 and 8 minutes ride out a short SES outage or throttling.
                email.setDueAt(now.plus(FIRST_RETRY.multipliedBy(1L << (attempts - 1))));
                log.warn("event=email_retry_scheduled emailId={} kind={} attempts={} exceptionClass={}",
                        email.getEmailId(), email.getKind(), attempts, ex.getClass().getName());
            }
            emails.save(email);
            return;
        }
        email.setStatus(EmailStatus.SENT);
        email.setSentAt(now);
        emails.save(email);
        log.info("event=email_sent emailId={} kind={}", email.getEmailId(), email.getKind());
    }

    private void drop(EmailEntity email, String reason) {
        email.setStatus(EmailStatus.DROPPED);
        emails.save(email);
        log.info("event=email_dropped emailId={} kind={} reason={}", email.getEmailId(), email.getKind(), reason);
    }
}
