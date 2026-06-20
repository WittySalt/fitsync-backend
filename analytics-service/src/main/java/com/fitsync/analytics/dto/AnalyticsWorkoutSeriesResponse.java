package com.fitsync.analytics.dto;

import java.time.LocalDate;
import java.util.List;

public record AnalyticsWorkoutSeriesResponse(
        LocalDate from,
        LocalDate to,
        String bucket,
        List<WorkoutSeriesPoint> points) {

    public AnalyticsWorkoutSeriesResponse {
        points = List.copyOf(points);
    }

    @Override
    public List<WorkoutSeriesPoint> points() {
        return List.copyOf(points);
    }

    public record WorkoutSeriesPoint(
            LocalDate date,
            int completedWorkoutCount,
            int totalWorkoutMinutes,
            int totalCaloriesBurned,
            int totalExerciseCount) {
    }
}
