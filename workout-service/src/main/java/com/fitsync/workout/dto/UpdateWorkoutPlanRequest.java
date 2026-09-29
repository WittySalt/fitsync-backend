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
        @Valid List<WorkoutPlanExerciseInput> exercises,
        @Valid @Size(max = 7) List<WorkoutPlanDayInput> days) {

    public UpdateWorkoutPlanRequest {
        exercises = exercises == null ? List.of() : List.copyOf(exercises);
        days = days == null ? List.of() : List.copyOf(days);
    }

    /** A request without a week, which replaces a plan with a flat one. */
    public UpdateWorkoutPlanRequest(String name,
            String description,
            GoalFocus goalFocus,
            WorkoutType workoutType,
            WorkoutDifficulty difficulty,
            Integer estimatedDurationMinutes,
            List<WorkoutPlanExerciseInput> exercises) {
        this(name, description, goalFocus, workoutType, difficulty, estimatedDurationMinutes, exercises, List.of());
    }

    @Override
    public List<WorkoutPlanExerciseInput> exercises() {
        return List.copyOf(exercises);
    }

    @Override
    public List<WorkoutPlanDayInput> days() {
        return List.copyOf(days);
    }
}
