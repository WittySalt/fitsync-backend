package com.fitsync.workout.dto;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "name": "Trap Bar Deadlift",
          "description": "Lower-body hinge movement with a neutral grip.",
          "exerciseType": "STRENGTH",
          "primaryMuscleGroup": "LEGS",
          "equipment": "BARBELL",
          "difficulty": "INTERMEDIATE"
        }
        """)
public record UpdateExerciseRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description,
        @NotNull ExerciseType exerciseType,
        @NotNull MuscleGroup primaryMuscleGroup,
        @NotNull ExerciseEquipment equipment,
        @NotNull WorkoutDifficulty difficulty) {
}
