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
        List<MuscleGroup> secondaryMuscleGroups,
        List<String> shortSteps) {

    public ExerciseView {
        instructions = instructions == null ? List.of() : List.copyOf(instructions);
        images = images == null ? List.of() : List.copyOf(images);
        secondaryMuscleGroups = secondaryMuscleGroups == null ? List.of() : List.copyOf(secondaryMuscleGroups);
        shortSteps = shortSteps == null ? List.of() : List.copyOf(shortSteps);
    }

    /** The detail shape without short steps, kept so callers that predate them stay valid. */
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
                        Instant updatedAt,
                        String videoUrl,
                        String animationUrl,
                        String externalSource,
                        String externalRef,
                        List<ExerciseInstructionView> instructions,
                        List<ExerciseImageView> images,
                        List<MuscleGroup> secondaryMuscleGroups) {
        this(exerciseId, ownerUserId, code, name, description, exerciseType, primaryMuscleGroup, equipment,
                difficulty, status, custom, createdAt, updatedAt, videoUrl, animationUrl, externalSource,
                externalRef, instructions, images, secondaryMuscleGroups, List.of());
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

    @Override
    public List<String> shortSteps() {
        return List.copyOf(shortSteps);
    }
}
