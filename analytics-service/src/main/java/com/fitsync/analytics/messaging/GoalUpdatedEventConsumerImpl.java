package com.fitsync.analytics.messaging;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.analytics.entity.AnalyticsUserDailyBodyEntity;
import com.fitsync.analytics.entity.AnalyticsProcessedEventEntity;
import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import com.fitsync.analytics.entity.AnalyticsUserGoalProgressEntity;
import com.fitsync.analytics.repository.AnalyticsProcessedEventRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import com.fitsync.analytics.service.AnalyticsCacheInvalidationService;
import com.fitsync.analytics.service.GoalProgressEvaluator;
import com.fitsync.analytics.service.GoalExpiryPolicy;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.GoalUpdatedPayload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles GoalUpdated: upserts the goal's progress row (or drops it when the goal was archived or cancelled),
 * marks an active goal past its target date as expired, mirrors calorie and protein targets onto the dashboard
 * snapshot and re-evaluates the goal. Redelivered events are skipped through the processed-event table.
 */
@Service
public class GoalUpdatedEventConsumerImpl implements DomainEventConsumer<GoalUpdatedPayload> {

    private static final String CALORIES = "CALORIES";
    private static final String PROTEIN = "PROTEIN";
    private static final String WEIGHT = "WEIGHT";

    private final AnalyticsProcessedEventRepository processedEventRepository;
    private final AnalyticsUserGoalProgressRepository goalProgressRepository;
    private final AnalyticsUserDailyBodyRepository dailyBodyRepository;
    private final AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository;
    private final AnalyticsCacheInvalidationService cacheInvalidationService;
    private final DashboardSnapshotInitializer dashboardSnapshotInitializer;
    private final GoalProgressEvaluator goalProgressEvaluator;
    private final Clock clock;

    public GoalUpdatedEventConsumerImpl(AnalyticsProcessedEventRepository processedEventRepository,
                                        AnalyticsUserGoalProgressRepository goalProgressRepository,
                                        AnalyticsUserDailyBodyRepository dailyBodyRepository,
                                        AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository,
                                        AnalyticsCacheInvalidationService cacheInvalidationService,
                                        DashboardSnapshotInitializer dashboardSnapshotInitializer,
                                        GoalProgressEvaluator goalProgressEvaluator,
                                        Clock clock) {
        this.processedEventRepository = processedEventRepository;
        this.goalProgressRepository = goalProgressRepository;
        this.dailyBodyRepository = dailyBodyRepository;
        this.dashboardSnapshotRepository = dashboardSnapshotRepository;
        this.cacheInvalidationService = cacheInvalidationService;
        this.dashboardSnapshotInitializer = dashboardSnapshotInitializer;
        this.goalProgressEvaluator = goalProgressEvaluator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<GoalUpdatedPayload> event) {
        if (processedEventRepository.existsById(event.eventId())) {
            return;
        }

        GoalUpdatedPayload payload = event.payload();
        if (payload == null) {
            recordProcessedEvent(event);
            return;
        }

        Instant evaluatedAt = event.occurredAt() != null ? event.occurredAt() : Instant.now(clock);
        String goalStatus = resolveGoalStatus(payload, evaluatedAt);

        if (isRemoved(payload.status())) {
            // Soft-deleted upstream: drop the row from the read model so it disappears
            // from goal-progress and stops counting toward dashboard targets. Guard the
            // delete so a removal event for a never-projected goal is a no-op.
            goalProgressRepository.findById(payload.goalId()).ifPresent(goalProgressRepository::delete);
            updateDashboardTargets(event, payload);
            recordProcessedEvent(event);
            cacheInvalidationService.evictForUser(event.userId());
            return;
        }

        AnalyticsUserGoalProgressEntity entity = goalProgressRepository.findById(payload.goalId())
                .orElseGet(AnalyticsUserGoalProgressEntity::new);
        entity.setGoalId(payload.goalId());
        entity.setUserId(event.userId());
        entity.setGoalType(payload.goalType());
        entity.setGoalStatus(goalStatus);
        entity.setTargetValue(payload.targetValue());
        entity.setTargetUnit(payload.targetUnit());
        entity.setTargetDate(payload.targetDate());
        captureWeightBaselineIfAvailable(event, payload, entity);
        entity.setLastEvaluatedAt(evaluatedAt);
        if (GoalExpiryPolicy.isActive(goalStatus)) {
            entity.setDaysRemaining(GoalExpiryPolicy.daysRemaining(
                    payload.targetDate(), evaluatedAt.atZone(ZoneOffset.UTC).toLocalDate()));
        } else {
            entity.setOnTrack(false);
            entity.setProgressRatio(null);
            entity.setCurrentValue(null);
            entity.setDaysRemaining(null);
        }
        goalProgressRepository.save(entity);

        updateDashboardTargets(event, payload);
        if (GoalExpiryPolicy.isActive(goalStatus) && payload.goalType() != null) {
            goalProgressEvaluator.recompute(
                    event.userId(),
                    Set.of(payload.goalType()),
                    goalProgressEvaluator.todayForUser(event.userId()));
        }
        recordProcessedEvent(event);
        cacheInvalidationService.evictForUser(event.userId());
    }

    private void captureWeightBaselineIfAvailable(FitSyncDomainEvent<GoalUpdatedPayload> event,
                                                  GoalUpdatedPayload payload,
                                                  AnalyticsUserGoalProgressEntity entity) {
        if (!WEIGHT.equals(payload.goalType()) || entity.getBaselineValue() != null) {
            return;
        }
        dailyBodyRepository.findTopByUserIdOrderByMetricDateDesc(event.userId())
                .map(AnalyticsUserDailyBodyEntity::getWeightKg)
                .ifPresent(entity::setBaselineValue);
    }

    private void updateDashboardTargets(FitSyncDomainEvent<GoalUpdatedPayload> event, GoalUpdatedPayload payload) {
        if (!CALORIES.equals(payload.goalType()) && !PROTEIN.equals(payload.goalType())) {
            return;
        }

        Instant generatedAt = event.occurredAt() != null ? event.occurredAt() : Instant.now(clock);
        dashboardSnapshotInitializer.insertIfMissing(event.userId(), generatedAt);
        AnalyticsUserDashboardSnapshotEntity snapshot = dashboardSnapshotRepository.findById(event.userId())
                .orElseThrow(() -> new IllegalStateException("Dashboard snapshot was not created"));

        boolean active = GoalExpiryPolicy.isActive(payload.status())
                && !GoalExpiryPolicy.isExpired(payload.targetDate(), generatedAt.atZone(ZoneOffset.UTC).toLocalDate());
        if (CALORIES.equals(payload.goalType())) {
            snapshot.setTodayCaloriesTarget(active && payload.targetValue() != null ? payload.targetValue().intValue() : null);
        } else {
            snapshot.setTodayProteinTargetGrams(active ? payload.targetValue() : null);
        }
        snapshot.setGeneratedAt(generatedAt);
        dashboardSnapshotRepository.save(snapshot);
    }

    private void recordProcessedEvent(FitSyncDomainEvent<?> event) {
        AnalyticsProcessedEventEntity processedEvent = new AnalyticsProcessedEventEntity();
        processedEvent.setEventId(event.eventId());
        processedEvent.setEventType(event.eventType());
        processedEvent.setUserId(event.userId());
        processedEvent.setProcessedAt(event.occurredAt() != null ? event.occurredAt() : Instant.now(clock));
        processedEventRepository.save(processedEvent);
    }

    private boolean isRemoved(String status) {
        String normalized = GoalExpiryPolicy.normalizeStatus(status);
        return "ARCHIVED".equals(normalized) || "CANCELLED".equals(normalized);
    }

    private String resolveGoalStatus(GoalUpdatedPayload payload, Instant evaluatedAt) {
        if (GoalExpiryPolicy.isActive(payload.status())
                && GoalExpiryPolicy.isExpired(payload.targetDate(), evaluatedAt.atZone(ZoneOffset.UTC).toLocalDate())) {
            return GoalExpiryPolicy.EXPIRED_STATUS;
        }
        return payload.status();
    }
}
