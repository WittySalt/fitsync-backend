package com.fitsync.workout.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSource;
import com.fitsync.workout.domain.WorkoutType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "planId": "22222222-2222-2222-2222-222222222222",
          "name": "Push Strength Day",
          "workoutType": "STRENGTH",
          "status": "COMPLETED",
          "source": "MANUAL",
          "startedAt": "2026-06-12T10:00:00Z",
          "completedAt": "2026-06-12T11:05:00Z",
          "durationMinutes": 65,
          "caloriesBurned": 420,
          "perceivedExertion": 8,
          "notes": "Felt strong on top sets.",
          "exercises": [
            {
              "exerciseId": "11111111-1111-1111-1111-111111111111",
              "sequence": 1,
              "performedSets": 3,
              "performedReps": 24,
              "totalVolumeKg": 1440.0,
              "distanceKm": null,
              "durationSeconds": null,
              "averageHeartRate": 132,
              "maxHeartRate": 158,
              "notes": "Last set was hard.",
              "sets": [
                {
                  "setNumber": 1,
                  "reps": 8,
                  "weightKg": 55.0,
                  "durationSeconds": null,
                  "distanceKm": null,
                  "restSeconds": 120,
                  "perceivedExertion": 7,
                  "notes": "Warm-up working set."
                }
              ]
            }
          ]
        }
        """)
public record UpsertWorkoutSessionRequest(
        UUID planId,
        @NotBlank @Size(max = 120) String name,
        @NotNull WorkoutType workoutType,
        @NotNull WorkoutSessionStatus status,
        @NotNull WorkoutSource source,
        @NotNull Instant startedAt,
        Instant completedAt,
        @Min(0) Integer durationMinutes,
        @Min(0) Integer caloriesBurned,
        @Min(1) @Max(10) Integer perceivedExertion,
        @Size(max = 1000) String notes,
        @Valid List<WorkoutExerciseLogInput> exercises,
        SportActivity sportActivity) {

    public UpsertWorkoutSessionRequest {
        exercises = exercises == null ? List.of() : List.copyOf(exercises);
    }

    /** A request without a sport activity, which is every request that is not a sport session. */
    public UpsertWorkoutSessionRequest(UUID planId,
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
            List<WorkoutExerciseLogInput> exercises) {
        this(planId, name, workoutType, status, source, startedAt, completedAt, durationMinutes,
                caloriesBurned, perceivedExertion, notes, exercises, null);
    }

    @Override
    public List<WorkoutExerciseLogInput> exercises() {
        return List.copyOf(exercises);
    }

    /** Bean Validation calls this by reflection, so it cannot be private. */
    @AssertTrue(message = "A sport session needs a sport activity.")
    boolean isSportActivityPresentWhenRequired() {
        return workoutType != WorkoutType.SPORTS || sportActivity != null;
    }
}
