package com.fitsync.workout.repository;

import java.util.UUID;

import com.fitsync.workout.domain.MuscleGroup;

/** One secondary muscle group of an exercise. */
public record ExerciseSecondaryRow(UUID exerciseId, MuscleGroup muscleGroup) {
}
