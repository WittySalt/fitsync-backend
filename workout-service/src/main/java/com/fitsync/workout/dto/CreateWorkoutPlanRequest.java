package com.fitsync.workout.dto;

import java.util.List;

import com.fitsync.workout.domain.GoalFocus;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "name": "Push Strength Day",
          "description": "Upper-body push workout with set-level targets.",
          "goalFocus": "STRENGTH",
          "workoutType": "STRENGTH",
          "difficulty": "INTERMEDIATE",
          "estimatedDurationMinutes": 60,
          "exercises": [
            {
              "exerciseId": "11111111-1111-1111-1111-111111111111",
              "sequence": 1,
              "targetSets": 3,
              "targetReps": 8,
              "targetWeightKg": 60.0,
              "targetDurationSeconds": null,
              "targetDistanceKm": null,
              "restSeconds": 120,
              "notes": "Controlled tempo.",
              "setTargets": [
                {
                  "setNumber": 1,
                  "targetReps": 8,
                  "targetWeightKg": 55.0,
                  "targetDurationSeconds": null,
                  "targetDistanceKm": null,
                  "restSeconds": 120
                },
                {
                  "setNumber": 2,
                  "targetReps": 8,
                  "targetWeightKg": 60.0,
                  "targetDurationSeconds": null,
                  "targetDistanceKm": null,
                  "restSeconds": 120
                }
              ]
            }
          ]
        }
        """)
public record CreateWorkoutPlanRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description,
        @NotNull GoalFocus goalFocus,
        @NotNull WorkoutType workoutType,
        @NotNull WorkoutDifficulty difficulty,
        @Min(1) Integer estimatedDurationMinutes,
        @Valid List<WorkoutPlanExerciseInput> exercises) {

    public CreateWorkoutPlanRequest {
        exercises = exercises == null ? List.of() : List.copyOf(exercises);
    }

    @Override
    public List<WorkoutPlanExerciseInput> exercises() {
        return List.copyOf(exercises);
    }
}
