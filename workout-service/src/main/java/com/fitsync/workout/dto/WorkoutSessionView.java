package com.fitsync.workout.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;
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
        Instant updatedAt,
        SessionKind kind,
        SportActivity sportActivity,
        String sportLabel) {

    public WorkoutSessionView {
        exercises = exercises == null ? List.of() : List.copyOf(exercises);
    }

    /** A view without a kind, used by fixtures that are not about the kind of a session. */
    public WorkoutSessionView(UUID sessionId,
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
        this(sessionId, planId, name, workoutType, status, source, startedAt, completedAt,
                durationMinutes, caloriesBurned, perceivedExertion, notes, exerciseCount, exercises,
                createdAt, updatedAt, null, null, null);
    }

    @Override
    public List<WorkoutExerciseLogView> exercises() {
        return List.copyOf(exercises);
    }
}
