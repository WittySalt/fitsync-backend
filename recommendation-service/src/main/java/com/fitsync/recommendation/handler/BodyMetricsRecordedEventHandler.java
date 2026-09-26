package com.fitsync.recommendation.handler;

import java.time.Instant;
import java.time.ZoneOffset;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.BodyMetricsRecordedPayload;
import com.fitsync.recommendation.domain.GoalTypes;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the latest body metrics on the context (inputs for the R1 TDEE estimate and
 * R3 goal pace). Out-of-order events never overwrite a newer measurement.
 */
@Service
public class BodyMetricsRecordedEventHandler implements DomainEventConsumer<BodyMetricsRecordedPayload> {

    private final RecommendationContextStore contextStore;
    private final ProcessedEventGuard processedEventGuard;

    public BodyMetricsRecordedEventHandler(RecommendationContextStore contextStore,
                                           ProcessedEventGuard processedEventGuard) {
        this.contextStore = contextStore;
        this.processedEventGuard = processedEventGuard;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<BodyMetricsRecordedPayload> event) {
        if (event.userId() == null || processedEventGuard.alreadyProcessed(event)) {
            return;
        }
        BodyMetricsRecordedPayload payload = event.payload();
        if (payload == null) {
            processedEventGuard.record(event);
            return;
        }

        RecommendationContextEntity context = contextStore.getOrCreate(event.userId());
        Instant measuredAt = payload.measuredAt() != null ? payload.measuredAt() : event.occurredAt();
        if (isNewerThanCurrent(context, measuredAt)) {
            if (payload.weightKg() != null) {
                context.setLatestWeightKg(payload.weightKg());
            }
            if (payload.bodyFatRate() != null) {
                context.setLatestBodyFatRate(payload.bodyFatRate());
            }
            context.setBodyMetricsRecordedAt(measuredAt);
        }
        fillGoalBaselineIfMissing(context, payload, measuredAt);
        contextStore.saveStale(context);
        processedEventGuard.record(event);
    }

    /**
     * A weight goal set before any weight was recorded has no R3 pace baseline yet;
     * the first weight that arrives afterwards becomes it.
     */
    private static void fillGoalBaselineIfMissing(RecommendationContextEntity context,
                                                  BodyMetricsRecordedPayload payload,
                                                  Instant measuredAt) {
        if (!GoalTypes.isWeightGoal(context.getCurrentGoalType())
                || context.getGoalBaselineWeightKg() != null
                || payload.weightKg() == null) {
            return;
        }
        context.setGoalBaselineWeightKg(payload.weightKg());
        context.setGoalBaselineOn(measuredAt == null
                ? null
                : measuredAt.atZone(ZoneOffset.UTC).toLocalDate());
    }

    private static boolean isNewerThanCurrent(RecommendationContextEntity context, Instant measuredAt) {
        if (measuredAt == null) {
            return context.getBodyMetricsRecordedAt() == null;
        }
        return context.getBodyMetricsRecordedAt() == null
                || !measuredAt.isBefore(context.getBodyMetricsRecordedAt());
    }
}
