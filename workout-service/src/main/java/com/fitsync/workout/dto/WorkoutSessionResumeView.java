package com.fitsync.workout.dto;

import java.util.UUID;

import com.fitsync.workout.domain.WorkoutSessionStatus;

public record WorkoutSessionResumeView(
        UUID sessionId,
        WorkoutSessionStatus status,
        Integer doneSets,
        Integer skippedSets,
        Integer pendingSets,
        Integer totalSets,
        WorkoutResumeSetView nextSet) {
}
