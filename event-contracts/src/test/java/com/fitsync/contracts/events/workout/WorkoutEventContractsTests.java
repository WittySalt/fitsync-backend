package com.fitsync.contracts.events.workout;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkoutEventContractsTests {

    @Test
    void shouldExposeWorkoutLoggedPayloadFields() {
        UUID sessionId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-06-11T18:30:00Z");
        Instant completedAt = Instant.parse("2026-06-11T19:20:00Z");

        WorkoutLoggedPayload payload = new WorkoutLoggedPayload(
                sessionId,
                planId,
                "STRENGTH",
                "COMPLETED",
                startedAt,
                completedAt,
                50,
                420,
                6,
                "PLAN");

        assertEquals(sessionId, payload.sessionId());
        assertEquals(planId, payload.planId());
        assertEquals("STRENGTH", payload.workoutType());
        assertEquals("COMPLETED", payload.status());
        assertEquals(startedAt, payload.startedAt());
        assertEquals(completedAt, payload.completedAt());
        assertEquals(50, payload.durationMinutes());
        assertEquals(420, payload.caloriesBurned());
        assertEquals(6, payload.exerciseCount());
        assertEquals("PLAN", payload.source());
    }

    @Test
    void shouldExposeWorkoutUpdatedPayloadFields() {
        UUID sessionId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-06-11T18:30:00Z");
        Instant completedAt = Instant.parse("2026-06-11T19:25:00Z");

        WorkoutUpdatedPayload payload = new WorkoutUpdatedPayload(
                sessionId,
                planId,
                "CARDIO",
                "COMPLETED",
                startedAt,
                completedAt,
                55,
                460,
                1,
                "DEVICE");

        assertEquals(sessionId, payload.sessionId());
        assertEquals(planId, payload.planId());
        assertEquals("CARDIO", payload.workoutType());
        assertEquals("COMPLETED", payload.status());
        assertEquals(startedAt, payload.startedAt());
        assertEquals(completedAt, payload.completedAt());
        assertEquals(55, payload.durationMinutes());
        assertEquals(460, payload.caloriesBurned());
        assertEquals(1, payload.exerciseCount());
        assertEquals("DEVICE", payload.source());
    }

    @Test
    void shouldExposeWorkoutDeletedPayloadFields() {
        UUID sessionId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Instant deletedAt = Instant.parse("2026-06-11T20:00:00Z");

        WorkoutDeletedPayload payload = new WorkoutDeletedPayload(sessionId, planId, deletedAt);

        assertEquals(sessionId, payload.sessionId());
        assertEquals(planId, payload.planId());
        assertEquals(deletedAt, payload.deletedAt());
    }
}
