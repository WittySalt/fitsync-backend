package com.fitsync.contracts.events.workout;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

    @Test
    void shouldExposePersonalRecordAchievedPayloadFields() {
        UUID exerciseId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant achievedAt = Instant.parse("2026-06-11T20:00:00Z");

        PersonalRecordAchievedPayload payload = new PersonalRecordAchievedPayload(
                exerciseId,
                "Barbell Bench Press",
                sessionId,
                List.of(new PersonalRecordAchievedPayload.RecordAchievement(
                        "MAX_WEIGHT_KG",
                        new BigDecimal("125.00"))),
                achievedAt);

        assertEquals(exerciseId, payload.exerciseId());
        assertEquals("Barbell Bench Press", payload.exerciseNameSnapshot());
        assertEquals(sessionId, payload.sessionId());
        assertEquals(1, payload.records().size());
        assertEquals("MAX_WEIGHT_KG", payload.records().get(0).recordType());
        assertEquals(new BigDecimal("125.00"), payload.records().get(0).value());
        assertEquals(achievedAt, payload.achievedAt());
    }

    @Test
    void shouldKeepOldConstructorsAndReadMissingEstimatedFlagAsNull() {
        Instant at = Instant.parse("2026-06-11T18:30:00Z");
        WorkoutLoggedPayload logged = new WorkoutLoggedPayload(
                UUID.randomUUID(), null, "STRENGTH", "COMPLETED", at, at, 50, 420, 6, "PLAN");
        WorkoutUpdatedPayload updated = new WorkoutUpdatedPayload(
                UUID.randomUUID(), null, "CARDIO", "COMPLETED", at, at, 55, 460, 1, "DEVICE");
        assertEquals(null, logged.caloriesEstimated());
        assertEquals(null, updated.caloriesEstimated());
    }
}
