package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fitsync.workout.domain.WorkoutSetStatus;

public record WorkoutExerciseSetLogView(
        UUID setLogId,
        Integer setNumber,
        WorkoutSetStatus status,
        Integer reps,
        BigDecimal weightKg,
        Integer durationSeconds,
        BigDecimal distanceKm,
        Integer restSeconds,
        Integer perceivedExertion,
        boolean logged,
        String notes,
        /** The rest actually taken after the set, or null when it was not timed. */
        Integer restTakenSeconds) {

    /** A set without a timed rest. */
    public WorkoutExerciseSetLogView(UUID setLogId, Integer setNumber, WorkoutSetStatus status, Integer reps,
                                     BigDecimal weightKg, Integer durationSeconds, BigDecimal distanceKm,
                                     Integer restSeconds, Integer perceivedExertion, boolean logged, String notes) {
        this(setLogId, setNumber, status, reps, weightKg, durationSeconds, distanceKm, restSeconds, perceivedExertion,
                logged, notes, null);
    }
}
