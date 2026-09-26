package com.fitsync.recommendation.domain;

/**
 * Goal-type vocabulary shared by the event handlers and the rule engine. The
 * {@code GoalUpdated} contract carries the type as a free string, so comparisons are
 * case-insensitive.
 */
public final class GoalTypes {

    public static final String WEIGHT = "WEIGHT";
    public static final String CALORIES = "CALORIES";

    private GoalTypes() {
    }

    public static boolean isWeightGoal(String goalType) {
        return goalType != null && String.CASE_INSENSITIVE_ORDER.compare(goalType, WEIGHT) == 0;
    }

    public static boolean isCalorieGoal(String goalType) {
        return goalType != null && String.CASE_INSENSITIVE_ORDER.compare(goalType, CALORIES) == 0;
    }
}
