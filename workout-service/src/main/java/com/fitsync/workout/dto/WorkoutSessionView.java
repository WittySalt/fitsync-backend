package com.fitsync.workout.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSource;
import com.fitsync.workout.domain.WorkoutType;

public record WorkoutSessionView(
        UUID sessionId,
        UUID planId,
        String name,
        WorkoutType workoutType,
        WorkoutSessionStatus status,
        WorkoutSource source,
        Instant startedAt,
        Instant completedAt,
        Integer durationMinutes,
        Integer caloriesBurned,
        Integer perceivedExertion,
        String notes,
        Integer exerciseCount,
        List<WorkoutExerciseLogView> exercises,
        Instant createdAt,
        Instant updatedAt) {
}
