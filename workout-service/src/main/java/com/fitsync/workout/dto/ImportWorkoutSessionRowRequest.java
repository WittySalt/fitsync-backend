package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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
        @Valid List<WorkoutExerciseLogInput> exercises,
        SportActivity sportActivity,
        @DecimalMin("0.0") @DecimalMax("1000.0") @Digits(integer = 4, fraction = 2) BigDecimal distanceKm) {

    public ImportWorkoutSessionRowRequest {
        exercises = exercises == null ? List.of() : List.copyOf(exercises);
    }

    /**
     * A row without a sport activity or a distance. An imported sport session without a sport
     * activity is stored as {@code OTHER}, because imported history cannot be asked again.
     */
    public ImportWorkoutSessionRowRequest(String externalId,
            String source,
            UUID planId,
            String name,
            WorkoutType workoutType,
            WorkoutSessionStatus status,
            Instant startedAt,
            Instant completedAt,
            Integer durationMinutes,
            Integer caloriesBurned,
            Integer perceivedExertion,
            String notes,
            List<WorkoutExerciseLogInput> exercises) {
        this(externalId, source, planId, name, workoutType, status, startedAt, completedAt,
                durationMinutes, caloriesBurned, perceivedExertion, notes, exercises, null, null);
    }

    /** A row without a distance. */
    public ImportWorkoutSessionRowRequest(String externalId,
            String source,
            UUID planId,
            String name,
            WorkoutType workoutType,
            WorkoutSessionStatus status,
            Instant startedAt,
            Instant completedAt,
            Integer durationMinutes,
            Integer caloriesBurned,
            Integer perceivedExertion,
            String notes,
            List<WorkoutExerciseLogInput> exercises,
            SportActivity sportActivity) {
        this(externalId, source, planId, name, workoutType, status, startedAt, completedAt,
                durationMinutes, caloriesBurned, perceivedExertion, notes, exercises, sportActivity, null);
    }

    @Override
    public List<WorkoutExerciseLogInput> exercises() {
        return List.copyOf(exercises);
    }
}
