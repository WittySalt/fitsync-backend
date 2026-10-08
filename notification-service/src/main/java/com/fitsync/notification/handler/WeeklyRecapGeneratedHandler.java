package com.fitsync.notification.handler;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.analytics.WeeklyRecapGeneratedPayload;
import com.fitsync.notification.domain.EmailKind;
import com.fitsync.notification.entity.GoalEntity;
import com.fitsync.notification.entity.RecipientEntity;
import com.fitsync.notification.entity.WeightEntity;
import com.fitsync.notification.repository.GoalRepository;
import com.fitsync.notification.repository.RecipientRepository;
import com.fitsync.notification.repository.WeightRepository;
import com.fitsync.notification.service.EmailComposer;
import com.fitsync.notification.service.EmailComposer.WeeklyReportFacts;
import com.fitsync.notification.service.EmailQueue;
import com.fitsync.notification.service.RecipientTime;

/**
 * Turns analytics' weekly recap into E5, due on the Monday after the week at 08:00 in the user's
 * timezone. A recap that arrives later than that is due at once, and one that could not go out
 * within a day is dropped, because a report on a week long gone helps nobody. A week without any
 * activity sends nothing.
 */
@Service
public class WeeklyRecapGeneratedHandler implements DomainEventConsumer<WeeklyRecapGeneratedPayload> {

    private static final LocalTime SEND_AT = LocalTime.of(8, 0);
    private static final Duration VALID_FOR = Duration.ofHours(24);

    private final RecipientRepository recipients;
    private final GoalRepository goals;
    private final WeightRepository weights;
    private final EmailQueue queue;
    private final EmailComposer composer;
    private final Clock clock;

    public WeeklyRecapGeneratedHandler(RecipientRepository recipients, GoalRepository goals,
                                       WeightRepository weights, EmailQueue queue, EmailComposer composer,
                                       Clock clock) {
        this.recipients = recipients;
        this.goals = goals;
        this.weights = weights;
        this.queue = queue;
        this.composer = composer;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<WeeklyRecapGeneratedPayload> event) {
        WeeklyRecapGeneratedPayload recap = event.payload();
        if (event.userId() == null || recap == null || recap.weekStart() == null || recap.weekEnd() == null
                || noActivity(recap)) {
            return;
        }
        UUID userId = event.userId();
        RecipientEntity recipient = recipients.findById(userId).orElse(null);
        ZoneId zone = RecipientTime.zoneOf(recipient);
        Instant now = clock.instant();

        LocalDate monday = recap.weekEnd().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Instant due = monday.atTime(SEND_AT).atZone(zone).toInstant();
        if (due.isBefore(now)) {
            due = now;
        }

        Instant weekFrom = recap.weekStart().atStartOfDay(zone).toInstant();
        Instant weekUntil = recap.weekEnd().plusDays(1).atStartOfDay(zone).toInstant();
        BigDecimal before = weights.findFirstByUserIdAndMeasuredAtLessThanOrderByMeasuredAtDesc(userId, weekFrom)
                .map(WeightEntity::getWeightKg).orElse(null);
        BigDecimal after = weights
                .findFirstByUserIdAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtDesc(
                        userId, weekFrom, weekUntil)
                .map(WeightEntity::getWeightKg).orElse(null);
        BigDecimal weeklyGoal = goals
                .findFirstByUserIdAndGoalTypeAndStatusOrderByUpdatedAtDesc(userId, "WORKOUT_FREQUENCY", "ACTIVE")
                .map(GoalEntity::getTargetValue).orElse(null);

        WeeklyReportFacts facts = new WeeklyReportFacts(recap.weekStart(), recap.weekEnd(), recap.workoutCount(),
                recap.workoutMinutes(), recap.activeDays(), recap.caloriesConsumed(), weeklyGoal, before, after);
        queue.queue(userId, EmailKind.WEEKLY_REPORT, recap.weekStart().toString(),
                composer.weeklyReport(recipient, facts), due, due.plus(VALID_FOR));
    }

    private static boolean noActivity(WeeklyRecapGeneratedPayload recap) {
        return recap.workoutCount() <= 0 && recap.workoutMinutes() <= 0 && recap.activeDays() <= 0
                && recap.caloriesConsumed() <= 0;
    }
}
