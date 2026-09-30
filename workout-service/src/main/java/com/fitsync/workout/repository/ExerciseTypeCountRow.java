package com.fitsync.workout.repository;

import com.fitsync.workout.domain.ExerciseType;

/** The raw grouped result of the catalogue summary query, before it is turned into the API shape. */
public record ExerciseTypeCountRow(ExerciseType exerciseType, long count) {
}
