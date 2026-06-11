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

@Schema(description = "Full replacement payload for a workout plan.",
        example = """
        {
          "name": "Push Strength Day",
          "description": "Updated upper-body push workout.",
          "goalFocus": "STRENGTH",
          "workoutType": "STRENGTH",
          "difficulty": "INTERMEDIATE",
          "estimatedDurationMinutes": 65,
          "exercises": [
            {
              "exerciseId": "11111111-1111-1111-1111-111111111111",
              "sequence": 1,
              "targetSets": 3,
              "targetReps": 8,
              "targetWeightKg": 62.5,
              "targetDurationSeconds": null,
              "targetDistanceKm": null,
              "restSeconds": 120,
              "notes": "Progressive overload.",
              "setTargets": [
                {
                  "setNumber": 1,
                  "targetReps": 8,
                  "targetWeightKg": 57.5,
                  "targetDurationSeconds": null,
                  "targetDistanceKm": null,
                  "restSeconds": 120
                }
              ]
            }
          ]
        }
        """)
public record UpdateWorkoutPlanRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description,
        @NotNull GoalFocus goalFocus,
        @NotNull WorkoutType workoutType,
        @NotNull WorkoutDifficulty difficulty,
        @Min(1) Integer estimatedDurationMinutes,
        @Valid List<WorkoutPlanExerciseInput> exercises) {
}
