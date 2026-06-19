package com.fitsync.workout.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "externalId": "strava-activity-12345",
          "source": "IMPORTED",
          "planId": null,
          "name": "Imported Run",
          "workoutType": "CARDIO",
          "status": "COMPLETED",
          "startedAt": "2026-06-12T10:00:00Z",
          "completedAt": "2026-06-12T10:42:00Z",
          "durationMinutes": 42,
          "caloriesBurned": 380,
          "perceivedExertion": 7,
          "notes": "Imported from Strava.",
          "exercises": []
        }
        """)
public record ImportWorkoutSessionRowRequest(
        @NotBlank @Size(max = 120) String externalId,
        @NotBlank @Size(max = 30) String source,
        UUID planId,
        @NotBlank @Size(max = 120) String name,
        @NotNull WorkoutType workoutType,
        @NotNull WorkoutSessionStatus status,
        @NotNull Instant startedAt,
        Instant completedAt,
        @Min(0) Integer durationMinutes,
        @Min(0) Integer caloriesBurned,
        @Min(1) @Max(10) Integer perceivedExertion,
        @Size(max = 1000) String notes,
        @Valid List<WorkoutExerciseLogInput> exercises) {

    public ImportWorkoutSessionRowRequest {
        exercises = exercises == null ? List.of() : List.copyOf(exercises);
    }

    @Override
    public List<WorkoutExerciseLogInput> exercises() {
        return List.copyOf(exercises);
    }
}
