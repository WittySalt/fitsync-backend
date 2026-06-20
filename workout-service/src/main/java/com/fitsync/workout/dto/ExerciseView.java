package com.fitsync.workout.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;

public record ExerciseView(
        UUID exerciseId,
        UUID ownerUserId,
        String code,
        String name,
        String description,
        ExerciseType exerciseType,
        MuscleGroup primaryMuscleGroup,
        ExerciseEquipment equipment,
        WorkoutDifficulty difficulty,
        ExerciseStatus status,
        boolean custom,
        Instant createdAt,
        Instant updatedAt,
        String videoUrl,
        String animationUrl,
        String externalSource,
        String externalRef,
        List<ExerciseInstructionView> instructions,
        List<ExerciseImageView> images,
        List<MuscleGroup> secondaryMuscleGroups) {

    public ExerciseView {
        instructions = instructions == null ? List.of() : List.copyOf(instructions);
        images = images == null ? List.of() : List.copyOf(images);
        secondaryMuscleGroups = secondaryMuscleGroups == null ? List.of() : List.copyOf(secondaryMuscleGroups);
    }

    public ExerciseView(UUID exerciseId,
                        UUID ownerUserId,
                        String code,
                        String name,
                        String description,
                        ExerciseType exerciseType,
                        MuscleGroup primaryMuscleGroup,
                        ExerciseEquipment equipment,
                        WorkoutDifficulty difficulty,
                        ExerciseStatus status,
                        boolean custom,
                        Instant createdAt,
                        Instant updatedAt) {
        this(
                exerciseId,
                ownerUserId,
                code,
                name,
                description,
                exerciseType,
                primaryMuscleGroup,
                equipment,
                difficulty,
                status,
                custom,
                createdAt,
                updatedAt,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of());
    }

    @Override
    public List<ExerciseInstructionView> instructions() {
        return List.copyOf(instructions);
    }

    @Override
    public List<ExerciseImageView> images() {
        return List.copyOf(images);
    }

    @Override
    public List<MuscleGroup> secondaryMuscleGroups() {
        return List.copyOf(secondaryMuscleGroups);
    }
}
