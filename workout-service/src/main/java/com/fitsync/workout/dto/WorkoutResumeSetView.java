package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.fitsync.workout.domain.WorkoutSetStatus;

public record WorkoutResumeSetView(
        UUID exerciseLogId,
        UUID exerciseId,
        String exerciseNameSnapshot,
        Integer exerciseSequence,
        UUID setLogId,
        Integer setNumber,
        WorkoutSetStatus status,
        Integer reps,
        BigDecimal weightKg,
        Integer durationSeconds,
        BigDecimal distanceKm,
        Integer restSeconds,
        Integer perceivedExertion,
        String notes) {
}
