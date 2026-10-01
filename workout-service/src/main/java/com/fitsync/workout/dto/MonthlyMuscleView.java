package com.fitsync.workout.dto;

import com.fitsync.workout.domain.MuscleGroup;

/** How many sessions of the month trained one muscle group. */
public record MonthlyMuscleView(MuscleGroup group, String label, int sessions) {
}
