package com.fitsync.recommendation.handler;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.TreeSet;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.GoalUpdatedPayload;
import com.fitsync.recommendation.domain.GoalTypes;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projects the user's current goal into the context (inputs for R1 nutrition gap and
 * R3 goal pace). A goal that is archived/cancelled upstream clears the goal fields.
 */
@Service
public class GoalUpdatedEventHandler implements DomainEventConsumer<GoalUpdatedPayload> {

    private static final Set<String> REMOVED_STATUSES = removedStatuses();

    private final RecommendationContextStore contextStore;
    private final ProcessedEventGuard processedEventGuard;

    public GoalUpdatedEventHandler(RecommendationContextStore contextStore,
                                   ProcessedEventGuard processedEventGuard) {
        this.contextStore = contextStore;
        this.processedEventGuard = processedEventGuard;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<GoalUpdatedPayload> event) {
        if (event.userId() == null || processedEventGuard.alreadyProcessed(event)) {
            return;
        }
        GoalUpdatedPayload payload = event.payload();
        if (payload == null) {
            processedEventGuard.record(event);
            return;
        }

        RecommendationContextEntity context = contextStore.getOrCreate(event.userId());
        if (payload.status() != null && REMOVED_STATUSES.contains(payload.status())) {
            context.setCurrentGoalType(null);
            context.setCurrentGoalTargetValue(null);
            context.setCurrentGoalTargetUnit(null);
            context.setCurrentGoalTargetDate(null);
            context.setGoalBaselineWeightKg(null);
            context.setGoalBaselineOn(null);
        } else {
            context.setCurrentGoalType(payload.goalType());
            context.setCurrentGoalTargetValue(payload.targetValue());
            context.setCurrentGoalTargetUnit(payload.targetUnit());
            context.setCurrentGoalTargetDate(payload.targetDate());
            captureBaseline(event, payload, context);
        }
        contextStore.saveStale(context);
        processedEventGuard.record(event);
    }

    /**
     * The R3 pace baseline is the weight known when the weight goal is (re)set. When no
     * weight was recorded yet, it stays empty and the first BodyMetricsRecorded after
     * the goal fills it (see {@link BodyMetricsRecordedEventHandler}).
     */
    private static void captureBaseline(FitSyncDomainEvent<GoalUpdatedPayload> event,
                                        GoalUpdatedPayload payload,
                                        RecommendationContextEntity context) {
        if (!GoalTypes.isWeightGoal(payload.goalType())) {
            context.setGoalBaselineWeightKg(null);
            context.setGoalBaselineOn(null);
            return;
        }
        context.setGoalBaselineWeightKg(context.getLatestWeightKg());
        context.setGoalBaselineOn(context.getLatestWeightKg() == null ? null : eventDate(event));
    }

    private static LocalDate eventDate(FitSyncDomainEvent<?> event) {
        return event.occurredAt() == null ? null : event.occurredAt().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static Set<String> removedStatuses() {
        Set<String> statuses = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        statuses.add("ARCHIVED");
        statuses.add("CANCELLED");
        return statuses;
    }
}
