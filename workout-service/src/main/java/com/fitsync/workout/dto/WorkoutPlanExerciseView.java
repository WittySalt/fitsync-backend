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
        /**
         * First catalog image for this exercise, or null when it has none. Carried here so a client
         * rendering a plan does not have to fetch every exercise's full detail just to read one URL:
         * the Today view was issuing one request per plan exercise for exactly that. Hibernate
         * batch-fetches the image collection, so this costs one extra query for the whole plan.
         */
        String primaryImageUrl,
        List<WorkoutPlanExerciseSetTargetView> setTargets,
        /** Seconds one rep takes when the exercise screen counts along, or null for the default of its kind. */
        BigDecimal tempoSeconds) {

    public WorkoutPlanExerciseView {
        setTargets = setTargets == null ? List.of() : List.copyOf(setTargets);
    }

    /** An exercise without a tempo of its own. */
    public WorkoutPlanExerciseView(UUID planExerciseId, UUID exerciseId, String exerciseNameSnapshot, Integer sequence,
                                   Integer targetSets, Integer targetReps, BigDecimal targetWeightKg,
                                   Integer targetDurationSeconds, BigDecimal targetDistanceKm, Integer restSeconds,
                                   String notes, String primaryImageUrl, List<WorkoutPlanExerciseSetTargetView> setTargets) {
        this(planExerciseId, exerciseId, exerciseNameSnapshot, sequence, targetSets, targetReps, targetWeightKg,
                targetDurationSeconds, targetDistanceKm, restSeconds, notes, primaryImageUrl, setTargets, null);
    }

    @Override
    public List<WorkoutPlanExerciseSetTargetView> setTargets() {
        return List.copyOf(setTargets);
    }
}
