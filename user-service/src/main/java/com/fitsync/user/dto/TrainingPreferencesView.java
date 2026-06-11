package com.fitsync.user.dto;

import java.time.LocalTime;

import com.fitsync.user.domain.FitnessLevel;
import com.fitsync.user.domain.IntensityPreference;
import com.fitsync.user.domain.TrainingLocation;

public record TrainingPreferencesView(
        FitnessLevel fitnessLevel,
        TrainingLocation trainingLocation,
        Integer weeklyWorkoutDays,
        Integer preferredDurationMinutes,
        LocalTime preferredWorkoutTime,
        IntensityPreference intensityPreference) {
}
