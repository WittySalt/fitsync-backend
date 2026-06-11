package com.fitsync.user.entity;

import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import com.fitsync.user.domain.CalorieTrackingMode;
import com.fitsync.user.domain.DietaryPreference;
import com.fitsync.user.domain.MacroPreference;
import com.fitsync.user.entity.converter.CodeEnumConverters;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_nutrition_preferences")
public class UserNutritionPreferencesEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Convert(converter = CodeEnumConverters.DietaryPreferenceConverter.class)
    @Column(name = "dietary_pattern_code", nullable = false, length = 40)
    private DietaryPreference dietaryPattern;

    @Column(name = "meal_frequency")
    private Integer mealFrequency;

    @Convert(converter = CodeEnumConverters.CalorieTrackingModeConverter.class)
    @Column(name = "calorie_tracking_mode_code", nullable = false, length = 40)
    private CalorieTrackingMode calorieTrackingMode;

    @Convert(converter = CodeEnumConverters.MacroPreferenceConverter.class)
    @Column(name = "macro_preference_code", nullable = false, length = 40)
    private MacroPreference macroPreference;

    @Column(name = "water_goal_ml")
    private Integer waterGoalMl;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public DietaryPreference getDietaryPattern() { return dietaryPattern; }
    public void setDietaryPattern(DietaryPreference dietaryPattern) { this.dietaryPattern = dietaryPattern; }
    public Integer getMealFrequency() { return mealFrequency; }
    public void setMealFrequency(Integer mealFrequency) { this.mealFrequency = mealFrequency; }
    public CalorieTrackingMode getCalorieTrackingMode() { return calorieTrackingMode; }
    public void setCalorieTrackingMode(CalorieTrackingMode calorieTrackingMode) { this.calorieTrackingMode = calorieTrackingMode; }
    public MacroPreference getMacroPreference() { return macroPreference; }
    public void setMacroPreference(MacroPreference macroPreference) { this.macroPreference = macroPreference; }
    public Integer getWaterGoalMl() { return waterGoalMl; }
    public void setWaterGoalMl(Integer waterGoalMl) { this.waterGoalMl = waterGoalMl; }
}
