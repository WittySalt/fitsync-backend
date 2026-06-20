package com.fitsync.user.dto;

import java.time.LocalTime;
import java.util.List;

import com.fitsync.user.domain.FitnessLevel;
import com.fitsync.user.domain.IntensityPreference;
import com.fitsync.user.domain.TrainingEquipment;
import com.fitsync.user.domain.TrainingFocusArea;
import com.fitsync.user.domain.TrainingLocation;

public record TrainingPreferencesView(
        FitnessLevel fitnessLevel,
        TrainingLocation trainingLocation,
        Integer weeklyWorkoutDays,
        Integer preferredDurationMinutes,
        LocalTime preferredWorkoutTime,
        IntensityPreference intensityPreference,
        List<TrainingEquipment> equipmentInventory,
        List<TrainingFocusArea> focusAreas) {

    public TrainingPreferencesView {
        equipmentInventory = equipmentInventory == null ? List.of() : List.copyOf(equipmentInventory);
        focusAreas = focusAreas == null ? List.of() : List.copyOf(focusAreas);
    }

    @Override
    public List<TrainingEquipment> equipmentInventory() {
        return List.copyOf(equipmentInventory);
    }

    @Override
    public List<TrainingFocusArea> focusAreas() {
        return List.copyOf(focusAreas);
    }
}
