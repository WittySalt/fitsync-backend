package com.fitsync.contracts.events.analytics;

import java.time.LocalDate;

/**
 * Emitted once per user per ISO week summarising the prior week's activity. The affected user is
 * carried on the {@code FitSyncDomainEvent} envelope.
 */
public record WeeklyRecapGeneratedPayload(
        LocalDate weekStart,
        LocalDate weekEnd,
        int workoutCount,
        int workoutMinutes,
        int caloriesBurned,
        int caloriesConsumed,
        int activeDays) {
}
