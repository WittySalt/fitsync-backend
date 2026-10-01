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
        List<WorkoutExerciseSetLogView> sets,
        /** The tempo this workout counts at, copied from the plan when it started, or null for the default of its kind. */
        BigDecimal tempoSeconds) {

    public WorkoutExerciseLogView {
        sets = sets == null ? List.of() : List.copyOf(sets);
    }

    /** An exercise log without a tempo of its own. */
    public WorkoutExerciseLogView(UUID exerciseLogId, UUID exerciseId, String exerciseNameSnapshot, Integer sequence,
                                  Integer performedSets, Integer performedReps, BigDecimal totalVolumeKg,
                                  BigDecimal distanceKm, Integer durationSeconds, Integer averageHeartRate,
                                  Integer maxHeartRate, String notes, List<WorkoutExerciseSetLogView> sets) {
        this(exerciseLogId, exerciseId, exerciseNameSnapshot, sequence, performedSets, performedReps, totalVolumeKg,
                distanceKm, durationSeconds, averageHeartRate, maxHeartRate, notes, sets, null);
    }

    @Override
    public List<WorkoutExerciseSetLogView> sets() {
        return List.copyOf(sets);
    }
}
