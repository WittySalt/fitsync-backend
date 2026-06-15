package com.fitsync.analytics.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalyticsDashboardResponse(
        Instant generatedAt,
        String timezone,
        BodySummary body,
        WorkoutSummary workout,
        NutritionSummary nutrition,
        List<GoalProgressSummary> goals) {

    public record BodySummary(
            Double latestWeightKg,
            Double latestBodyFatRate,
            Double bmi,
            Double weightChange30d) {
    }

    public record WorkoutSummary(
            int todayWorkoutCount,
            int weekWorkoutCount,
            int weekWorkoutMinutes,
            int activeStreakDays) {
    }

    public record NutritionSummary(
            Integer todayCaloriesConsumed,
            Integer todayCaloriesTarget,
            Double todayProteinGrams,
            Double todayProteinTargetGrams) {
    }

    public record GoalProgressSummary(
            UUID goalId,
            String goalType,
            Double progressRatio,
            boolean onTrack) {
    }
}
