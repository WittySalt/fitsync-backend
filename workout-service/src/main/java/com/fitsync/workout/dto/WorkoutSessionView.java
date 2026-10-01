package com.fitsync.workout.dto;

import java.math.BigDecimal;
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
        String sportLabel,
        BigDecimal distanceKm,
        UUID planDayId,
        Instant autoClosedAt,
        /** When the session ended, completed or cancelled; null while it is in progress. */
        Instant endedAt,
        /** The calories, or the effort, were estimated by the server rather than entered by the person. */
        boolean caloriesEstimated,
        boolean effortEstimated,
        /** Personal records the session set; given only when it was just completed or counted, else empty. */
        List<NewRecordView> newRecords) {

    public WorkoutSessionView {
        exercises = exercises == null ? List.of() : List.copyOf(exercises);
        newRecords = newRecords == null ? List.of() : List.copyOf(newRecords);
    }

    /** A view without the end, the estimate flags and the new records. */
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
            Instant updatedAt,
            SessionKind kind,
            SportActivity sportActivity,
            String sportLabel,
            BigDecimal distanceKm,
            UUID planDayId,
            Instant autoClosedAt) {
        this(sessionId, planId, name, workoutType, status, source, startedAt, completedAt,
                durationMinutes, caloriesBurned, perceivedExertion, notes, exerciseCount, exercises,
                createdAt, updatedAt, kind, sportActivity, sportLabel, distanceKm, planDayId, autoClosedAt,
                completedAt, false, false, List.of());
    }

    /** The same view with the records the session just set. */
    public WorkoutSessionView withNewRecords(List<NewRecordView> records) {
        return new WorkoutSessionView(sessionId, planId, name, workoutType, status, source, startedAt, completedAt,
                durationMinutes, caloriesBurned, perceivedExertion, notes, exerciseCount, exercises, createdAt, updatedAt,
                kind, sportActivity, sportLabel, distanceKm, planDayId, autoClosedAt, endedAt, caloriesEstimated,
                effortEstimated, records);
    }

    @Override
    public List<NewRecordView> newRecords() {
        return List.copyOf(newRecords);
    }

    /** A view without the plan day and the auto-close time, for callers written before those fields. */
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
            Instant updatedAt,
            SessionKind kind,
            SportActivity sportActivity,
            String sportLabel,
            BigDecimal distanceKm) {
        this(sessionId, planId, name, workoutType, status, source, startedAt, completedAt,
                durationMinutes, caloriesBurned, perceivedExertion, notes, exerciseCount, exercises,
                createdAt, updatedAt, kind, sportActivity, sportLabel, distanceKm, null, null,
                completedAt, false, false, List.of());
    }

    /** A view without a kind, sport activity, sport label or distance, used by fixtures that are
     * not about those fields of a session. */
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
                createdAt, updatedAt, null, null, null, null, null, null, completedAt, false, false, List.of());
    }

    @Override
    public List<WorkoutExerciseLogView> exercises() {
        return List.copyOf(exercises);
    }
}
