package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WorkoutExerciseSetLogView(
        UUID setLogId,
        Integer setNumber,
        Integer reps,
        BigDecimal weightKg,
        Integer durationSeconds,
        BigDecimal distanceKm,
        Integer restSeconds,
        Integer perceivedExertion,
        String notes) {
}
