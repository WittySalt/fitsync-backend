package com.fitsync.workout.dto;

import java.util.UUID;

import com.fitsync.workout.domain.WorkoutSource;

public record ImportWorkoutSessionRowResult(
        int rowIndex,
        String externalId,
        WorkoutSource source,
        ImportWorkoutSessionRowStatus status,
        UUID sessionId,
        String reason) {
}
