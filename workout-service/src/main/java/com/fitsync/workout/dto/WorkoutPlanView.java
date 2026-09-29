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
        /** Only the exercises that belong to no plan day; empty (never all of them) when the plan has a week. */
        List<WorkoutPlanExerciseView> exercises,
        Instant createdAt,
        Instant updatedAt,
        /** Empty for a flat plan; one entry per scheduled day of the week otherwise. */
        List<WorkoutPlanDayView> days,
        /** The number of scheduled days, or null for a flat plan. */
        Integer sessionsPerWeek) {

    public WorkoutPlanView {
        exercises = exercises == null ? List.of() : List.copyOf(exercises);
        days = days == null ? List.of() : List.copyOf(days);
    }

    /** A view of a flat plan, which has no week. */
    public WorkoutPlanView(UUID planId,
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
        this(planId, name, description, goalFocus, workoutType, difficulty, estimatedDurationMinutes, status,
                exercises, createdAt, updatedAt, List.of(), null);
    }

    @Override
    public List<WorkoutPlanExerciseView> exercises() {
        return List.copyOf(exercises);
    }

    @Override
    public List<WorkoutPlanDayView> days() {
        return List.copyOf(days);
    }
}
