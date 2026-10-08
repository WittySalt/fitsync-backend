package com.fitsync.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fitsync.user.domain.ActivityLevel;
import com.fitsync.user.domain.CalorieTrackingMode;
import com.fitsync.user.domain.DietaryPreference;
import com.fitsync.user.domain.FitnessLevel;
import com.fitsync.user.domain.Gender;
import com.fitsync.user.domain.IntensityPreference;
import com.fitsync.user.domain.LanguagePreference;
import com.fitsync.user.domain.MacroPreference;
import com.fitsync.user.domain.PrimaryGoal;
import com.fitsync.user.domain.PrivacyLevel;
import com.fitsync.user.domain.TrainingEquipment;
import com.fitsync.user.domain.TrainingFocusArea;
import com.fitsync.user.domain.TrainingLocation;
import com.fitsync.user.domain.UnitSystem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CompleteOnboardingRequest(
        @NotBlank @Size(min = 2, max = 80) String displayName,
        @Size(max = 80) String firstName,
        @Size(max = 80) String lastName,
        LocalDate birthDate,
        Gender gender,
        @NotNull @DecimalMin("80.0") @DecimalMax("300.0") BigDecimal heightCm,
        @NotNull ActivityLevel activityLevel,
        @Size(max = 80) String timezone,
        @NotNull UnitSystem preferredUnits,
        LanguagePreference language,
        PrivacyLevel privacyLevel,
        @NotNull @DecimalMin("1.0") @DecimalMax("500.0") BigDecimal currentWeightKg,
        @NotNull @DecimalMin("1.0") @DecimalMax("500.0") BigDecimal targetWeightKg,
        @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal bodyFatRate,
        LocalDate targetDate,
        @NotNull FitnessLevel fitnessLevel,
        @NotNull TrainingLocation trainingLocation,
        @Min(1) @Max(14) Integer weeklyWorkoutDays,
        @NotNull @Min(5) @Max(240) Integer preferredDurationMinutes,
        LocalTime preferredWorkoutTime,
        @NotNull IntensityPreference intensityPreference,
        @Size(max = 8) List<@NotNull TrainingEquipment> equipmentInventory,
        @Size(max = 6) List<@NotNull TrainingFocusArea> focusAreas,
        @NotNull DietaryPreference dietaryPattern,
        @NotNull @Min(1) @Max(12) Integer mealFrequency,
        @NotNull CalorieTrackingMode calorieTrackingMode,
        @NotNull MacroPreference macroPreference,
        @Min(0) @Max(10000) Integer waterGoalMl,
        // Daily targets the user saw (and may have edited) on the plan step. Each one is
        // optional; a missing one is estimated on the server from the body data above.
        @Min(1000) @Max(6000) Integer targetCalories,
        @Min(20) @Max(400) Integer targetProteinGrams,
        @Min(0) @Max(900) Integer targetCarbsGrams,
        @Min(10) @Max(300) Integer targetFatGrams,
        boolean workoutReminderEnabled,
        boolean mealReminderEnabled,
        boolean weighInReminderEnabled,
        @Valid UpsertHealthReadinessRequest healthReadiness,
        PrimaryGoal primaryGoal) {

    public CompleteOnboardingRequest {
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
