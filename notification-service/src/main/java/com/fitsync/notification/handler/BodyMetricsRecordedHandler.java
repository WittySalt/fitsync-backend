package com.fitsync.notification.handler;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.BodyMetricsRecordedPayload;
import com.fitsync.notification.domain.EmailKind;
import com.fitsync.notification.entity.GoalEntity;
import com.fitsync.notification.entity.RecipientEntity;
import com.fitsync.notification.entity.WeightEntity;
import com.fitsync.notification.repository.GoalRepository;
import com.fitsync.notification.repository.RecipientRepository;
import com.fitsync.notification.repository.WeightRepository;
import com.fitsync.notification.service.EmailComposer;
import com.fitsync.notification.service.EmailQueue;
import com.fitsync.notification.service.RecipientTime;

/**
 * Stores each new weight and checks it against the user's active weight goals (E4). A goal is
 * reached when the weight gets to the target from the side the goal started on, so a goal to lose
 * weight is reached at or below the target and a goal to gain at or above it. Daily calorie and
 * macro targets never count. The goal id is the dedupe key, so each goal sends at most once.
 */
@Service
public class BodyMetricsRecordedHandler implements DomainEventConsumer<BodyMetricsRecordedPayload> {

    public static final String WEIGHT = "WEIGHT";
    public static final String ACTIVE = "ACTIVE";

    private final WeightRepository weights;
    private final GoalRepository goals;
    private final RecipientRepository recipients;
    private final EmailQueue queue;
    private final EmailComposer composer;
    private final Clock clock;

    public BodyMetricsRecordedHandler(WeightRepository weights, GoalRepository goals, RecipientRepository recipients,
                                      EmailQueue queue, EmailComposer composer, Clock clock) {
        this.weights = weights;
        this.goals = goals;
        this.recipients = recipients;
        this.queue = queue;
        this.composer = composer;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<BodyMetricsRecordedPayload> event) {
        BodyMetricsRecordedPayload payload = event.payload();
        if (event.userId() == null || payload == null || payload.weightKg() == null) {
            return;
        }
        Instant now = clock.instant();
        UUID metricId = payload.metricId() != null ? payload.metricId() : event.eventId();
        if (metricId == null || weights.existsById(metricId)) {
            return;
        }
        Instant measuredAt = payload.measuredAt() != null ? payload.measuredAt()
                : event.occurredAt() != null ? event.occurredAt() : now;
        BigDecimal weight = payload.weightKg();
        weights.save(new WeightEntity(metricId, event.userId(), weight, measuredAt));

        RecipientEntity recipient = recipients.findById(event.userId()).orElse(null);
        for (GoalEntity goal : goals.findByUserIdAndGoalTypeAndStatus(event.userId(), WEIGHT, ACTIVE)) {
            if (goal.getStartWeightKg() == null) {
                // A goal set before any weight was known takes this one as its start.
                goal.setStartWeightKg(weight);
                goals.save(goal);
            }
            if (reached(goal.getStartWeightKg(), goal.getTargetValue(), weight)) {
                ZoneId zone = RecipientTime.zoneOf(recipient);
                long days = Math.max(0, ChronoUnit.DAYS.between(
                        goal.getStartedAt().atZone(zone).toLocalDate(), measuredAt.atZone(zone).toLocalDate()));
                queue.queue(event.userId(), EmailKind.GOAL_REACHED, goal.getGoalId().toString(),
                        composer.goalReached(recipient, goal.getTargetValue(), goal.getStartWeightKg(), weight, days),
                        now, null);
            }
        }
    }

    /** True when the weight reached the target in the goal's direction; no direction means no email. */
    public static boolean reached(BigDecimal start, BigDecimal target, BigDecimal weight) {
        if (start == null || target == null) {
            return false;
        }
        int direction = start.compareTo(target);
        if (direction > 0) {
            return weight.compareTo(target) <= 0;
        }
        if (direction < 0) {
            return weight.compareTo(target) >= 0;
        }
        return false;
    }
}
