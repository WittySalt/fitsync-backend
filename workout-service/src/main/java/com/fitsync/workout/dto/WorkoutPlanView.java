package com.fitsync.workout.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.GoalFocus;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutPlanStatus;
import com.fitsync.workout.domain.WorkoutType;

public record WorkoutPlanView(
        UUID planId,
        String name,
        String description,
        GoalFocus goalFocus,
        WorkoutType workoutType,
        WorkoutDifficulty difficulty,
        Integer estimatedDurationMinutes,
        WorkoutPlanStatus status,
        List<WorkoutPlanExerciseView> exercises,
        Instant createdAt,
        Instant updatedAt) {

    public WorkoutPlanView {
        exercises = exercises == null ? List.of() : List.copyOf(exercises);
    }

    @Override
    public List<WorkoutPlanExerciseView> exercises() {
        return List.copyOf(exercises);
    }
}
