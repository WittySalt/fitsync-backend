package com.fitsync.analytics.messaging;

import java.time.Instant;

import com.fitsync.analytics.entity.AnalyticsProcessedEventEntity;
import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import com.fitsync.analytics.entity.AnalyticsUserGoalProgressEntity;
import com.fitsync.analytics.repository.AnalyticsProcessedEventRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import com.fitsync.analytics.service.AnalyticsCacheInvalidationService;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.GoalUpdatedPayload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalUpdatedEventConsumerImpl implements GoalUpdatedEventConsumer {

    private static final String ACTIVE = "ACTIVE";
    private static final String CALORIES = "CALORIES";
    private static final String PROTEIN = "PROTEIN";

    private final AnalyticsProcessedEventRepository processedEventRepository;
    private final AnalyticsUserGoalProgressRepository goalProgressRepository;
    private final AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository;
    private final AnalyticsCacheInvalidationService cacheInvalidationService;
    private final DashboardSnapshotInitializer dashboardSnapshotInitializer;

    public GoalUpdatedEventConsumerImpl(AnalyticsProcessedEventRepository processedEventRepository,
                                        AnalyticsUserGoalProgressRepository goalProgressRepository,
                                        AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository,
                                        AnalyticsCacheInvalidationService cacheInvalidationService,
                                        DashboardSnapshotInitializer dashboardSnapshotInitializer) {
        this.processedEventRepository = processedEventRepository;
        this.goalProgressRepository = goalProgressRepository;
        this.dashboardSnapshotRepository = dashboardSnapshotRepository;
        this.cacheInvalidationService = cacheInvalidationService;
        this.dashboardSnapshotInitializer = dashboardSnapshotInitializer;
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

        AnalyticsUserGoalProgressEntity entity = goalProgressRepository.findById(payload.goalId())
                .orElseGet(AnalyticsUserGoalProgressEntity::new);
        entity.setGoalId(payload.goalId());
        entity.setUserId(event.userId());
        entity.setGoalType(payload.goalType());
        entity.setGoalStatus(payload.status());
        entity.setTargetValue(payload.targetValue());
        entity.setTargetUnit(payload.targetUnit());
        entity.setTargetDate(payload.targetDate());
        entity.setLastEvaluatedAt(event.occurredAt() != null ? event.occurredAt() : Instant.now());
        entity.setOnTrack(ACTIVE.equals(payload.status()));
        if (!ACTIVE.equals(payload.status())) {
            entity.setProgressRatio(null);
            entity.setCurrentValue(null);
            entity.setDaysRemaining(null);
        }
        goalProgressRepository.save(entity);

        updateDashboardTargets(event, payload);
        recordProcessedEvent(event);
        cacheInvalidationService.evictAllForUser(event.userId());
    }

    private void updateDashboardTargets(FitSyncDomainEvent<GoalUpdatedPayload> event, GoalUpdatedPayload payload) {
        if (!CALORIES.equals(payload.goalType()) && !PROTEIN.equals(payload.goalType())) {
            return;
        }

        Instant generatedAt = event.occurredAt() != null ? event.occurredAt() : Instant.now();
        dashboardSnapshotInitializer.insertIfMissing(event.userId(), generatedAt);
        AnalyticsUserDashboardSnapshotEntity snapshot = dashboardSnapshotRepository.findById(event.userId())
                .orElseThrow(() -> new IllegalStateException("Dashboard snapshot was not created"));

        boolean active = ACTIVE.equals(payload.status());
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
        processedEvent.setProcessedAt(event.occurredAt() != null ? event.occurredAt() : Instant.now());
        processedEventRepository.save(processedEvent);
    }
}
