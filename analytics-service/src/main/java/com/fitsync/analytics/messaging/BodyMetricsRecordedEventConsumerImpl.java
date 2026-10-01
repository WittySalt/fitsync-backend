package com.fitsync.analytics.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.analytics.entity.AnalyticsProcessedEventEntity;
import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyBodyEntity;
import com.fitsync.analytics.repository.AnalyticsProcessedEventRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import com.fitsync.analytics.service.AnalyticsCacheInvalidationService;
import com.fitsync.analytics.service.GoalProgressEvaluator;
import com.fitsync.analytics.service.GoalExpiryPolicy;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.BodyMetricsRecordedPayload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BodyMetricsRecordedEventConsumerImpl implements DomainEventConsumer<BodyMetricsRecordedPayload> {

    private final AnalyticsProcessedEventRepository processedEventRepository;
    private final AnalyticsUserDailyBodyRepository dailyBodyRepository;
    private final AnalyticsUserGoalProgressRepository goalProgressRepository;
    private final AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository;
    private final AnalyticsCacheInvalidationService cacheInvalidationService;
    private final DashboardSnapshotInitializer dashboardSnapshotInitializer;
    private final GoalProgressEvaluator goalProgressEvaluator;

    public BodyMetricsRecordedEventConsumerImpl(AnalyticsProcessedEventRepository processedEventRepository,
                                                AnalyticsUserDailyBodyRepository dailyBodyRepository,
                                                AnalyticsUserGoalProgressRepository goalProgressRepository,
                                                AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository,
                                                AnalyticsCacheInvalidationService cacheInvalidationService,
                                                DashboardSnapshotInitializer dashboardSnapshotInitializer,
                                                GoalProgressEvaluator goalProgressEvaluator) {
        this.processedEventRepository = processedEventRepository;
        this.dailyBodyRepository = dailyBodyRepository;
        this.goalProgressRepository = goalProgressRepository;
        this.dashboardSnapshotRepository = dashboardSnapshotRepository;
        this.cacheInvalidationService = cacheInvalidationService;
        this.dashboardSnapshotInitializer = dashboardSnapshotInitializer;
        this.goalProgressEvaluator = goalProgressEvaluator;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<BodyMetricsRecordedPayload> event) {
        if (processedEventRepository.existsById(event.eventId())) {
            return;
        }

        BodyMetricsRecordedPayload payload = event.payload();
        if (payload == null || payload.measuredAt() == null) {
            recordProcessedEvent(event);
            return;
        }

        LocalDate metricDate = goalProgressEvaluator.activityDateForUser(event.userId(), payload.measuredAt());
        AnalyticsUserDailyBodyEntity entity = dailyBodyRepository.findByUserIdAndMetricDate(event.userId(), metricDate)
                .orElseGet(AnalyticsUserDailyBodyEntity::new);
        entity.setUserId(event.userId());
        entity.setMetricDate(metricDate);
        entity.setWeightKg(payload.weightKg());
        entity.setBodyFatRate(payload.bodyFatRate());
        entity.setWaistCm(payload.waistCm());
        entity.setWeightDeltaFromPrevious(resolveWeightDeltaFromPrevious(event.userId(), metricDate, payload.weightKg()));
        entity.setWeightDeltaFromStart(resolveWeightDeltaFromStart(event.userId(), payload.weightKg()));
        entity.setProjectionUpdatedAt(resolveOccurredAt(event.occurredAt(), payload.measuredAt()));
        dailyBodyRepository.save(entity);

        Instant generatedAt = resolveOccurredAt(event.occurredAt(), payload.measuredAt());
        dashboardSnapshotInitializer.insertIfMissing(event.userId(), generatedAt);
        AnalyticsUserDashboardSnapshotEntity snapshot = dashboardSnapshotRepository.findById(event.userId())
                .orElseThrow(() -> new IllegalStateException("Dashboard snapshot was not created"));
        snapshot.setLatestWeightKg(payload.weightKg());
        snapshot.setLatestBodyFatRate(payload.bodyFatRate());
        snapshot.setWeightChange30d(resolveWeightChange30d(event.userId(), metricDate, payload.weightKg()));
        applyLatestWaist(snapshot, payload.waistCm(), metricDate);
        snapshot.setGeneratedAt(generatedAt);
        dashboardSnapshotRepository.save(snapshot);

        captureMissingWeightBaselines(event.userId(), payload.weightKg());
        goalProgressEvaluator.recompute(
                event.userId(),
                Set.of(GoalProgressEvaluator.WEIGHT),
                goalProgressEvaluator.todayForUser(event.userId()));
        recordProcessedEvent(event);
        cacheInvalidationService.evictForUser(event.userId());
    }

    private BigDecimal resolveWeightDeltaFromPrevious(java.util.UUID userId, LocalDate metricDate, BigDecimal weightKg) {
        if (weightKg == null) {
            return null;
        }
        return dailyBodyRepository.findTopByUserIdAndMetricDateBeforeOrderByMetricDateDesc(userId, metricDate)
                .map(AnalyticsUserDailyBodyEntity::getWeightKg)
                .filter(previousWeight -> previousWeight != null)
                .map(weightKg::subtract)
                .orElse(null);
    }

    private BigDecimal resolveWeightDeltaFromStart(java.util.UUID userId, BigDecimal weightKg) {
        if (weightKg == null) {
            return null;
        }
        return dailyBodyRepository.findTopByUserIdOrderByMetricDateAsc(userId)
                .map(AnalyticsUserDailyBodyEntity::getWeightKg)
                .filter(startWeight -> startWeight != null)
                .map(weightKg::subtract)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal resolveWeightChange30d(java.util.UUID userId, LocalDate metricDate, BigDecimal weightKg) {
        if (weightKg == null) {
            return null;
        }
        return dailyBodyRepository.findTopByUserIdAndMetricDateLessThanEqualOrderByMetricDateDesc(
                        userId,
                        metricDate.minusDays(30))
                .map(AnalyticsUserDailyBodyEntity::getWeightKg)
                .filter(baseline -> baseline != null)
                .map(weightKg::subtract)
                .orElse(null);
    }

    /**
     * The waist is optional on the event and many entries carry weight only, so an absent waist
     * leaves the earlier one in place. A measurement dated before the stored waist is ignored so a
     * late-arriving older event cannot roll the figure back.
     */
    private void applyLatestWaist(AnalyticsUserDashboardSnapshotEntity snapshot, BigDecimal waistCm,
                                  LocalDate metricDate) {
        if (waistCm == null) {
            return;
        }
        LocalDate current = snapshot.getWaistMeasuredOn();
        if (current != null && metricDate.isBefore(current)) {
            return;
        }
        snapshot.setLatestWaistCm(waistCm);
        snapshot.setWaistMeasuredOn(metricDate);
    }

    private Instant resolveOccurredAt(Instant occurredAt, Instant measuredAt) {
        return occurredAt != null ? occurredAt : measuredAt;
    }

    private void captureMissingWeightBaselines(java.util.UUID userId, BigDecimal baselineValue) {
        if (baselineValue == null) {
            return;
        }
        goalProgressRepository.findByUserIdAndGoalStatusAndGoalTypeInOrderByTargetDateAscGoalIdAsc(
                        userId, GoalExpiryPolicy.ACTIVE_STATUS, Set.of(GoalProgressEvaluator.WEIGHT))
                .stream()
                .filter(goal -> goal.getBaselineValue() == null)
                .forEach(goal -> {
                    goal.setBaselineValue(baselineValue);
                    goalProgressRepository.save(goal);
                });
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
