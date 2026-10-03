package com.fitsync.analytics.dto;

import java.time.LocalDate;

/**
 * Both windows are dates in the user's own zone. {@code currentToDate} is true while the current
 * period is not over, which means the current window stops at today and the previous window covers
 * the same number of days, so a client should word the comparison as "so far".
 */
public record AnalyticsCompareResponse(
        String period,
        PeriodWindow currentWindow,
        PeriodWindow previousWindow,
        WorkoutComparison workout,
        NutritionComparison nutrition,
        boolean currentToDate) {

    public record PeriodWindow(LocalDate from, LocalDate to) {
    }

    public record MetricComparison(Double current, Double previous, Double delta, Double deltaPct) {
    }

    /** Number of days that count as logged in each window: days with a workout, or days with a meal. */
    public record DaysLogged(int current, int previous) {
    }

    public record WorkoutComparison(
            MetricComparison completedWorkoutCount,
            MetricComparison totalWorkoutMinutes,
            MetricComparison totalCaloriesBurned,
            MetricComparison totalExerciseCount,
            DaysLogged daysLogged) {
    }

    /**
     * Averages per logged day, so a window with fewer logged days is not read as eating less. A side
     * with no logged day is null, and so are the delta fields that need both sides.
     */
    public record AveragePerLoggedDay(
            MetricComparison calories,
            MetricComparison proteinGrams,
            MetricComparison carbsGrams,
            MetricComparison fatGrams) {
    }

    public record NutritionComparison(
            MetricComparison totalCaloriesConsumed,
            MetricComparison totalProteinGrams,
            MetricComparison totalCarbsGrams,
            MetricComparison totalFatGrams,
            DaysLogged daysLogged,
            AveragePerLoggedDay averagePerLoggedDay) {
    }
}
