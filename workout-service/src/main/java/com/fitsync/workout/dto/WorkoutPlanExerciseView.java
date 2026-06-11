package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record WorkoutPlanExerciseView(
        UUID planExerciseId,
        UUID exerciseId,
        String exerciseNameSnapshot,
        Integer sequence,
        Integer targetSets,
        Integer targetReps,
        BigDecimal targetWeightKg,
        Integer targetDurationSeconds,
        BigDecimal targetDistanceKm,
        Integer restSeconds,
        String notes,
        List<WorkoutPlanExerciseSetTargetView> setTargets) {

    public WorkoutPlanExerciseView {
        setTargets = setTargets == null ? List.of() : List.copyOf(setTargets);
    }

    @Override
    public List<WorkoutPlanExerciseSetTargetView> setTargets() {
        return List.copyOf(setTargets);
    }
}
