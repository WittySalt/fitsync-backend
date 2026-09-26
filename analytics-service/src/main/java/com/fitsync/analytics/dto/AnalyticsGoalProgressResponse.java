package com.fitsync.analytics.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AnalyticsGoalProgressResponse(
        List<GoalProgressView> goals) {

    public AnalyticsGoalProgressResponse {
        goals = List.copyOf(goals);
    }

    @Override
    public List<GoalProgressView> goals() {
        return List.copyOf(goals);
    }

    /**
     * {@code expired} is derived at read time from {@code targetDate}, exactly as the dashboard's
     * own goal summary does. The two shapes must agree: this endpoint is what the dashboard page
     * actually calls, so fixing only the embedded summary left the visible defect in place.
     */
    public record GoalProgressView(
            UUID goalId,
            String goalType,
            String goalStatus,
            Double targetValue,
            String targetUnit,
            LocalDate targetDate,
            Double currentValue,
            Double progressRatio,
            Integer daysRemaining,
            boolean onTrack,
            boolean expired,
            Instant lastEvaluatedAt) {
    }
}
