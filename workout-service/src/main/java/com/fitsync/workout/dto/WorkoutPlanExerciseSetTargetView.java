package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WorkoutPlanExerciseSetTargetView(
        UUID setTargetId,
        Integer setNumber,
        Integer targetReps,
        BigDecimal targetWeightKg,
        Integer targetDurationSeconds,
        BigDecimal targetDistanceKm,
        Integer restSeconds) {
}
