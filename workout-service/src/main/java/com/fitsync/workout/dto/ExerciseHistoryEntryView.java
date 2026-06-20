package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "sessionId": "33333333-3333-3333-3333-333333333333",
          "exerciseLogId": "44444444-4444-4444-4444-444444444444",
          "setLogId": "55555555-5555-5555-5555-555555555555",
          "exerciseId": "11111111-1111-1111-1111-111111111111",
          "exerciseNameSnapshot": "Barbell Bench Press",
          "sessionName": "Push Strength Day",
          "startedAt": "2026-06-12T10:00:00Z",
          "completedAt": "2026-06-12T11:05:00Z",
          "sequence": 1,
          "setNumber": 2,
          "reps": 5,
          "weightKg": 100.0,
          "durationSeconds": null,
          "distanceKm": null,
          "restSeconds": 180,
          "perceivedExertion": 8,
          "notes": "Top set."
        }
        """)
public record ExerciseHistoryEntryView(
        UUID sessionId,
        UUID exerciseLogId,
        UUID setLogId,
        UUID exerciseId,
        String exerciseNameSnapshot,
        String sessionName,
        Instant startedAt,
        Instant completedAt,
        Integer sequence,
        Integer setNumber,
        Integer reps,
        BigDecimal weightKg,
        Integer durationSeconds,
        BigDecimal distanceKm,
        Integer restSeconds,
        Integer perceivedExertion,
        String notes) {
}
