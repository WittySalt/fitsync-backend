package com.fitsync.user.dto;

import com.fitsync.user.domain.CalorieTrackingMode;
import com.fitsync.user.domain.DietaryPreference;
import com.fitsync.user.domain.MacroPreference;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Nutrition preference enum fields are serialized as stable code values.",
        example = """
        {
          "dietaryPattern": "300001",
          "mealFrequency": 4,
          "calorieTrackingMode": "310002",
          "macroPreference": "320002",
          "waterGoalMl": 2500
        }
        """)
public record UpsertNutritionPreferencesRequest(
        @NotNull DietaryPreference dietaryPattern,
        @Min(1) @Max(12) Integer mealFrequency,
        @NotNull CalorieTrackingMode calorieTrackingMode,
        @NotNull MacroPreference macroPreference,
        @Min(0) @Max(10000) Integer waterGoalMl) {
}
