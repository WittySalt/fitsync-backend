package com.fitsync.recommendation.handler;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.TreeSet;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.GoalUpdatedPayload;
import com.fitsync.recommendation.domain.GoalTypes;
import com.fitsync.recommendation.domain.UserTimeZones;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projects the user's goals into the context (inputs for R1 nutrition gap, R2 weekly
 * adherence and R3 goal pace). A user can hold several goals at once — WEIGHT, CALORIES,
 * PROTEIN and WORKOUT_FREQUENCY all arrive on this same event type — so CALORIES, PROTEIN
 * and WORKOUT_FREQUENCY goals are projected into their own dedicated fields instead of the
 * single "current goal" slot, which stays reserved for WEIGHT (and any other goal type
 * not handled specially). A goal that is archived/cancelled upstream clears only the
 * field(s) that goal type owns; it never clears a different goal type's data.
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
        } else if (GoalTypes.isCalorieGoal(payload.goalType())) {
            context.setCalorieGoalKcal(removed ? null : calorieGoalKcal(payload));
        } else if (removed) {
            clearCurrentGoalSlot(payload, context);
        } else {
            boolean sameGoal = isSameWeightGoal(payload, context);
            context.setCurrentGoalType(payload.goalType());
            context.setCurrentGoalTargetValue(payload.targetValue());
            context.setCurrentGoalTargetUnit(payload.targetUnit());
            context.setCurrentGoalTargetDate(payload.targetDate());
            if (payload.goalId() != null) {
                context.setCurrentGoalId(payload.goalId());
            }
            if (!sameGoal) {
                captureBaseline(event, payload, context);
            }
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
        context.setCurrentGoalId(null);
        context.setGoalBaselineWeightKg(null);
        context.setGoalBaselineOn(null);
    }

    /** A missing or non-positive target value means "no goal" rather than a target of nothing. */
    private static Integer calorieGoalKcal(GoalUpdatedPayload payload) {
        if (payload.targetValue() == null || payload.targetValue().signum() <= 0) {
            return null;
        }
        return payload.targetValue().intValue();
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
     * An update of the weight goal already in the slot: the same goal id, or, for a row written
     * before goal ids were kept, a weight goal whose baseline is already set. Its baseline stays.
     */
    private static boolean isSameWeightGoal(GoalUpdatedPayload payload, RecommendationContextEntity context) {
        if (!GoalTypes.isWeightGoal(payload.goalType()) || !GoalTypes.isWeightGoal(context.getCurrentGoalType())) {
            return false;
        }
        if (payload.goalId() != null && context.getCurrentGoalId() != null) {
            return payload.goalId().equals(context.getCurrentGoalId());
        }
        return context.getGoalBaselineOn() != null;
    }

    /**
     * A new weight goal starts today, in the user's zone. Its R3 pace baseline is the weight on that
     * day: the latest weigh-in when it was taken on the start day, else the first weigh-in that
     * arrives afterwards (see {@link BodyMetricsRecordedEventHandler}); until then the weight stays
     * empty and the date holds the goal's start. Later weigh-ins never move it, the same start the
     * Progress goal road uses.
     */
    private static void captureBaseline(FitSyncDomainEvent<GoalUpdatedPayload> event,
                                        GoalUpdatedPayload payload,
                                        RecommendationContextEntity context) {
        if (!GoalTypes.isWeightGoal(payload.goalType())) {
            context.setGoalBaselineWeightKg(null);
            context.setGoalBaselineOn(null);
            return;
        }
        ZoneId zone = UserTimeZones.resolve(context.getTimezone());
        LocalDate startedOn = event.occurredAt() == null ? null : event.occurredAt().atZone(zone).toLocalDate();
        LocalDate weighedOn = context.getBodyMetricsRecordedAt() == null ? null
                : context.getBodyMetricsRecordedAt().atZone(zone).toLocalDate();
        if (context.getLatestWeightKg() != null && weighedOn != null
                && (startedOn == null || !weighedOn.isBefore(startedOn))) {
            context.setGoalBaselineWeightKg(context.getLatestWeightKg());
            context.setGoalBaselineOn(weighedOn);
            return;
        }
        context.setGoalBaselineWeightKg(null);
        context.setGoalBaselineOn(startedOn);
    }

    private static Set<String> removedStatuses() {
        Set<String> statuses = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        statuses.add("ARCHIVED");
        statuses.add("CANCELLED");
        return statuses;
    }
}
