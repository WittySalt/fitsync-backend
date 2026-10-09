package com.fitsync.workout.dto;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The caller's full personal data held by workout-service, returned for a data export request. The frontend bundles
 * it with the exports of the other services, so the service name travels with the document.
 */
@Schema(description = "Everything workout-service holds for the signed-in caller")
public record WorkoutDataExport(
        @Schema(example = "workout-service")
        String service,

        @Schema(example = "2026-10-09T12:00:00Z")
        Instant exportedAt,

        List<WorkoutSessionView> sessions,
        List<WorkoutPlanView> plans,
        List<ExerciseView> customExercises) {

    /** The value of {@link #service()} for every export this service produces. */
    public static final String SERVICE_NAME = "workout-service";

    public WorkoutDataExport {
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
        plans = plans == null ? List.of() : List.copyOf(plans);
        customExercises = customExercises == null ? List.of() : List.copyOf(customExercises);
    }

    @Override
    public List<WorkoutSessionView> sessions() {
        return List.copyOf(sessions);
    }

    @Override
    public List<WorkoutPlanView> plans() {
        return List.copyOf(plans);
    }

    @Override
    public List<ExerciseView> customExercises() {
        return List.copyOf(customExercises);
    }
}
