package com.fitsync.user.dto;

import com.fitsync.user.domain.CalorieTrackingMode;
import com.fitsync.user.domain.DietaryPreference;
import com.fitsync.user.domain.MacroPreference;

public record NutritionPreferencesView(
        DietaryPreference dietaryPattern,
        Integer mealFrequency,
        CalorieTrackingMode calorieTrackingMode,
        MacroPreference macroPreference,
        Integer waterGoalMl) {
}
