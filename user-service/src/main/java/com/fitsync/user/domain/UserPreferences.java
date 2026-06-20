package com.fitsync.user.domain;

import java.util.List;
import java.util.UUID;

public record UserPreferences(
        UUID userId,
        UnitSystem preferredUnits,
        LanguagePreference language,
        String timezone,
        PrivacyLevel privacyLevel,
        FitnessLevel fitnessLevel,
        TrainingLocation trainingLocation,
        List<TrainingEquipment> equipmentInventory,
        List<TrainingFocusArea> focusAreas,
        DietaryPreference dietaryPattern,
        CalorieTrackingMode calorieTrackingMode,
        MacroPreference macroPreference,
        boolean pushEnabled,
        boolean emailEnabled) {

    public UserPreferences {
        equipmentInventory = equipmentInventory == null ? List.of() : List.copyOf(equipmentInventory);
        focusAreas = focusAreas == null ? List.of() : List.copyOf(focusAreas);
    }
}
