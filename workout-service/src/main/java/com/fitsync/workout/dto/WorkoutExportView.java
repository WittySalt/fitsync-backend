package com.fitsync.workout.dto;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Portable export document containing the caller's workout data")
public record WorkoutExportView(
        @Schema(example = "1")
        int schemaVersion,

        @Schema(example = "2026-06-19T12:00:00Z")
        Instant exportedAt,

        List<ExerciseView> exercises,
        List<WorkoutPlanView> plans,
        List<WorkoutSessionView> sessions) {

    public WorkoutExportView {
        exercises = exercises == null ? List.of() : List.copyOf(exercises);
        plans = plans == null ? List.of() : List.copyOf(plans);
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
    }

    @Override
    public List<ExerciseView> exercises() {
        return List.copyOf(exercises);
    }

    @Override
    public List<WorkoutPlanView> plans() {
        return List.copyOf(plans);
    }

    @Override
    public List<WorkoutSessionView> sessions() {
        return List.copyOf(sessions);
    }
}
