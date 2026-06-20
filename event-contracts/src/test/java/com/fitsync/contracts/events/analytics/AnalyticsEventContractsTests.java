package com.fitsync.contracts.events.analytics;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalyticsEventContractsTests {

    @Test
    void shouldExposeStreakMilestoneReachedPayloadFields() {
        LocalDate achievedDate = LocalDate.parse("2026-06-18");

        StreakMilestoneReachedPayload payload = new StreakMilestoneReachedPayload(30, 30, achievedDate);

        assertEquals(30, payload.streakDays());
        assertEquals(30, payload.milestone());
        assertEquals(achievedDate, payload.achievedDate());
    }

    @Test
    void shouldExposeWeeklyRecapGeneratedPayloadFields() {
        LocalDate weekStart = LocalDate.parse("2026-06-08");
        LocalDate weekEnd = LocalDate.parse("2026-06-14");

        WeeklyRecapGeneratedPayload payload = new WeeklyRecapGeneratedPayload(
                weekStart, weekEnd, 4, 210, 1800, 12600, 5);

        assertEquals(weekStart, payload.weekStart());
        assertEquals(weekEnd, payload.weekEnd());
        assertEquals(4, payload.workoutCount());
        assertEquals(210, payload.workoutMinutes());
        assertEquals(1800, payload.caloriesBurned());
        assertEquals(12600, payload.caloriesConsumed());
        assertEquals(5, payload.activeDays());
    }
}
