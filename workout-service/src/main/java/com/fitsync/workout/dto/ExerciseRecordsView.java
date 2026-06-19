package com.fitsync.workout.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "exerciseId": "11111111-1111-1111-1111-111111111111",
          "exerciseName": "Barbell Bench Press",
          "maxWeightKg": {"value": 125.0, "sessionId": "33333333-3333-3333-3333-333333333333", "achievedAt": "2026-06-12T11:05:00Z"},
          "maxReps": {"value": 12, "sessionId": "33333333-3333-3333-3333-333333333333", "achievedAt": "2026-06-12T11:05:00Z"},
          "maxSingleSetVolumeKg": {"value": 600.0, "sessionId": "33333333-3333-3333-3333-333333333333", "achievedAt": "2026-06-12T11:05:00Z"},
          "estimatedOneRepMaxKg": {"value": 145.8, "sessionId": "33333333-3333-3333-3333-333333333333", "achievedAt": "2026-06-12T11:05:00Z"},
          "bestSessionVolumeKg": {"value": 1800.0, "sessionId": "33333333-3333-3333-3333-333333333333", "achievedAt": "2026-06-12T11:05:00Z"}
        }
        """)
public record ExerciseRecordsView(
        UUID exerciseId,
        String exerciseName,
        ExerciseRecordValueView maxWeightKg,
        ExerciseRecordValueView maxReps,
        ExerciseRecordValueView maxSingleSetVolumeKg,
        ExerciseRecordValueView estimatedOneRepMaxKg,
        ExerciseRecordValueView bestSessionVolumeKg) {
}
