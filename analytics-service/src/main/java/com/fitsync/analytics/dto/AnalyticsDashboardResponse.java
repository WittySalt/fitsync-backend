package com.fitsync.analytics.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AnalyticsDashboardResponse(
        Instant generatedAt,
        String timezone,
        BodySummary body,
        WorkoutSummary workout,
        NutritionSummary nutrition,
        List<GoalProgressSummary> goals) {

    public AnalyticsDashboardResponse {
        goals = List.copyOf(goals);
    }

    @Override
    public List<GoalProgressSummary> goals() {
        return List.copyOf(goals);
    }

    /**
     * {@code measuredOn} is the day the newest body measurement was taken. It used to be absent,
     * which left the client captioning the "Latest body metric" card with the snapshot's generation
     * time because that was the only date in the payload.
     */
    public record BodySummary(
            Double latestWeightKg,
            Double latestBodyFatRate,
            Double bmi,
            Double weightChange30d,
            LocalDate measuredOn) {
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

    /**
     * {@code expired} is derived at read time from {@code targetDate}. Goal expiry was already
     * applied when events were consumed and by the nightly rollup, but nothing re-evaluated a goal
     * whose date passed in between, so the stored status stayed ACTIVE and the client kept showing
     * "on track" against a deadline that had gone.
     */
    public record GoalProgressSummary(
            UUID goalId,
            String goalType,
            Double targetValue,
            String targetUnit,
            LocalDate targetDate,
            Double currentValue,
            Double progressRatio,
            boolean onTrack,
            boolean expired,
            Instant lastEvaluatedAt) {
    }
}
