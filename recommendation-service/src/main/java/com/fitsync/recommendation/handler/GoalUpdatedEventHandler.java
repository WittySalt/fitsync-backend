package com.fitsync.recommendation.handler;

import java.util.Set;
import java.util.TreeSet;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.GoalUpdatedPayload;
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
        } else {
            context.setCurrentGoalType(payload.goalType());
            context.setCurrentGoalTargetValue(payload.targetValue());
            context.setCurrentGoalTargetUnit(payload.targetUnit());
            context.setCurrentGoalTargetDate(payload.targetDate());
        }
        contextStore.saveStale(context);
        processedEventGuard.record(event);
    }

    private static Set<String> removedStatuses() {
        Set<String> statuses = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        statuses.add("ARCHIVED");
        statuses.add("CANCELLED");
        return statuses;
    }
}
