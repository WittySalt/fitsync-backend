package com.fitsync.analytics.dto;

import java.time.LocalDate;

public record AnalyticsWorkoutSummaryResponse(
        LocalDate from,
        LocalDate to,
        int totalWorkoutCount,
        int totalWorkoutMinutes,
        int totalCaloriesBurned,
        int totalExerciseCount,
        Double averageDurationMinutes,
        int activeStreakDays) {
}
