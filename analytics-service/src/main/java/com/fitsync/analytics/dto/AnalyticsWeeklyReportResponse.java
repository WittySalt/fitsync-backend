package com.fitsync.analytics.dto;

import java.time.LocalDate;

public record AnalyticsWeeklyReportResponse(
        LocalDate from,
        LocalDate to,
        int workoutCount,
        int workoutMinutes,
        int caloriesBurned,
        Integer avgDailyCaloriesConsumed,
        int proteinTargetHitDays,
        Double weightDeltaKg,
        WeekOverWeekDelta vsPreviousWeek) {

    public record WeekOverWeekDelta(
            int workoutCount,
            int workoutMinutes) {
    }
}
