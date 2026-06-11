package com.fitsync.workout.dto;

import java.time.Instant;
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
        Instant updatedAt) {
}
