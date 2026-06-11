package com.fitsync.user.domain;

import java.util.UUID;

public record UserPreferences(
        UUID userId,
        UnitSystem preferredUnits,
        LanguagePreference language,
        String timezone,
        PrivacyLevel privacyLevel,
        FitnessLevel fitnessLevel,
        TrainingLocation trainingLocation,
        DietaryPreference dietaryPattern,
        CalorieTrackingMode calorieTrackingMode,
        MacroPreference macroPreference,
        boolean pushEnabled,
        boolean emailEnabled) {
}
