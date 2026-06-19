package com.fitsync.workout.dto;

import java.util.List;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.GoalFocus;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Workout enum vocabularies for exercise and workout forms")
public record ExerciseMetadataView(
        List<ExerciseType> exerciseTypes,
        List<MuscleGroup> muscleGroups,
        List<ExerciseEquipment> equipment,
        List<WorkoutDifficulty> difficulties,
        List<WorkoutType> workoutTypes,
        List<GoalFocus> goalFocuses) {

    public ExerciseMetadataView {
        exerciseTypes = exerciseTypes == null ? List.of() : List.copyOf(exerciseTypes);
        muscleGroups = muscleGroups == null ? List.of() : List.copyOf(muscleGroups);
        equipment = equipment == null ? List.of() : List.copyOf(equipment);
        difficulties = difficulties == null ? List.of() : List.copyOf(difficulties);
        workoutTypes = workoutTypes == null ? List.of() : List.copyOf(workoutTypes);
        goalFocuses = goalFocuses == null ? List.of() : List.copyOf(goalFocuses);
    }

    @Override
    public List<ExerciseType> exerciseTypes() {
        return List.copyOf(exerciseTypes);
    }

    @Override
    public List<MuscleGroup> muscleGroups() {
        return List.copyOf(muscleGroups);
    }

    @Override
    public List<ExerciseEquipment> equipment() {
        return List.copyOf(equipment);
    }

    @Override
    public List<WorkoutDifficulty> difficulties() {
        return List.copyOf(difficulties);
    }

    @Override
    public List<WorkoutType> workoutTypes() {
        return List.copyOf(workoutTypes);
    }

    @Override
    public List<GoalFocus> goalFocuses() {
        return List.copyOf(goalFocuses);
    }
}
