package com.fitsync.analytics.dto;

import java.time.LocalDate;

public record AnalyticsCompareResponse(
        String period,
        PeriodWindow currentWindow,
        PeriodWindow previousWindow,
        WorkoutComparison workout,
        NutritionComparison nutrition) {

    public record PeriodWindow(LocalDate from, LocalDate to) {
    }

    public record MetricComparison(Double current, Double previous, Double delta, Double deltaPct) {
    }

    public record WorkoutComparison(
            MetricComparison completedWorkoutCount,
            MetricComparison totalWorkoutMinutes,
            MetricComparison totalCaloriesBurned,
            MetricComparison totalExerciseCount) {
    }

    public record NutritionComparison(
            MetricComparison totalCaloriesConsumed,
            MetricComparison totalProteinGrams,
            MetricComparison totalCarbsGrams,
            MetricComparison totalFatGrams) {
    }
}
