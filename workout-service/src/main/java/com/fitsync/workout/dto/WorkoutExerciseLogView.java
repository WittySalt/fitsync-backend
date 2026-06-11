package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record WorkoutExerciseLogView(
        UUID exerciseLogId,
        UUID exerciseId,
        String exerciseNameSnapshot,
        Integer sequence,
        Integer performedSets,
        Integer performedReps,
        BigDecimal totalVolumeKg,
        BigDecimal distanceKm,
        Integer durationSeconds,
        Integer averageHeartRate,
        Integer maxHeartRate,
        String notes,
        List<WorkoutExerciseSetLogView> sets) {
}
