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
 * Projects the user's goals into the context (inputs for R1 nutrition gap, R2 weekly
 * adherence and R3 goal pace). A user can hold several goals at once — WEIGHT, CALORIES,
 * PROTEIN and WORKOUT_FREQUENCY all arrive on this same event type — so PROTEIN and
 * WORKOUT_FREQUENCY goals are projected into their own dedicated fields instead of the
 * single "current goal" slot, which stays reserved for WEIGHT/CALORIES (and any other
 * goal type not handled specially). A goal that is archived/cancelled upstream clears
 * only the field(s) that goal type owns; it never clears a different goal type's data.
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
        boolean removed = payload.status() != null && REMOVED_STATUSES.contains(payload.status());
        if (GoalTypes.isProteinGoal(payload.goalType())) {
            context.setProteinTargetGrams(removed ? null : payload.targetValue());
        } else if (GoalTypes.isWorkoutFrequencyGoal(payload.goalType())) {
            context.setWeeklyWorkoutGoalDays(removed ? null : weeklyWorkoutGoalDays(payload));
        } else if (removed) {
            clearCurrentGoalSlot(payload, context);
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
     * The removed goal only owns the slot when its type matches what is currently
     * projected there (case-insensitively), or when the payload carries no goal type at
     * all — the latter keeps the pre-existing behaviour for that legacy event shape.
     * Otherwise the slot belongs to a different goal and is left untouched.
     */
    private static void clearCurrentGoalSlot(GoalUpdatedPayload payload, RecommendationContextEntity context) {
        if (payload.goalType() != null
                && (context.getCurrentGoalType() == null
                        || String.CASE_INSENSITIVE_ORDER.compare(payload.goalType(), context.getCurrentGoalType()) != 0)) {
            return;
        }
        context.setCurrentGoalType(null);
        context.setCurrentGoalTargetValue(null);
        context.setCurrentGoalTargetUnit(null);
        context.setCurrentGoalTargetDate(null);
        context.setGoalBaselineWeightKg(null);
        context.setGoalBaselineOn(null);
    }

    /**
     * A missing or non-positive target value means "no goal" rather than "target zero
     * workouts a week".
     */
    private static Integer weeklyWorkoutGoalDays(GoalUpdatedPayload payload) {
        if (payload.targetValue() == null || payload.targetValue().signum() <= 0) {
            return null;
        }
        return payload.targetValue().intValue();
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
