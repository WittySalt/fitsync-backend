package com.fitsync.contracts.events.workout;

import java.time.Instant;
import java.util.UUID;

public record WorkoutUpdatedPayload(
        UUID sessionId,
        UUID planId,
        String workoutType,
        String status,
        Instant startedAt,
        Instant completedAt,
        Integer durationMinutes,
        Integer caloriesBurned,
        Integer exerciseCount,
        String source,
        Boolean caloriesEstimated) {

    /** Shape before {@code caloriesEstimated} existed; the flag stays null and reads as false. */
    public WorkoutUpdatedPayload(
            UUID sessionId, UUID planId, String workoutType, String status, Instant startedAt,
            Instant completedAt, Integer durationMinutes, Integer caloriesBurned,
            Integer exerciseCount, String source) {
        this(sessionId, planId, workoutType, status, startedAt, completedAt, durationMinutes,
                caloriesBurned, exerciseCount, source, null);
    }
}
