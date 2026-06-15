package com.fitsync.analytics.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AnalyticsGoalProgressResponse(
        List<GoalProgressView> goals) {

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
            Instant lastEvaluatedAt) {
    }
}
