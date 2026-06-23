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
          "code": "trap-bar-deadlift",
          "name": "Trap Bar Deadlift",
          "description": "Lower-body hinge movement with a neutral grip.",
          "exerciseType": "STRENGTH",
          "primaryMuscleGroup": "LEGS",
          "equipment": "BARBELL",
          "difficulty": "INTERMEDIATE",
          "videoUrl": "https://media.example.com/exercises/trap-bar-deadlift.mp4"
        }
        """)
public record CreateExerciseRequest(
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description,
        @NotNull ExerciseType exerciseType,
        @NotNull MuscleGroup primaryMuscleGroup,
        @NotNull ExerciseEquipment equipment,
        @NotNull WorkoutDifficulty difficulty,
        @Size(max = 500) String videoUrl) {

    public CreateExerciseRequest(String code,
                                 String name,
                                 String description,
                                 ExerciseType exerciseType,
                                 MuscleGroup primaryMuscleGroup,
                                 ExerciseEquipment equipment,
                                 WorkoutDifficulty difficulty) {
        this(code, name, description, exerciseType, primaryMuscleGroup, equipment, difficulty, null);
    }
}
