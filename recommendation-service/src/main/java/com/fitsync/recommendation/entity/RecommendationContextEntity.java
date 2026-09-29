package com.fitsync.recommendation.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Per-user read model assembled from domain + analytics events (README §7). Shared input
 * for both the rule engine and the AI tier. Consumers update it and flip {@link #stale};
 * generation reads it. It owns no primary write path in other services.
 */
@Entity
@Table(name = "recommendation_context")
public class RecommendationContextEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // Current goal (from GoalUpdated) — no calorie target is carried in the event.
    @Column(name = "current_goal_type", length = 40)
    private String currentGoalType;

    @Column(name = "current_goal_target_value", precision = 12, scale = 3)
    private BigDecimal currentGoalTargetValue;

    @Column(name = "current_goal_target_unit", length = 20)
    private String currentGoalTargetUnit;

    @Column(name = "current_goal_target_date")
    private LocalDate currentGoalTargetDate;

    // Weight (and date) when the current weight goal was set — the R3 pace baseline.
    @Column(name = "goal_baseline_weight_kg", precision = 6, scale = 2)
    private BigDecimal goalBaselineWeightKg;

    @Column(name = "goal_baseline_on")
    private LocalDate goalBaselineOn;

    // Profile inputs for the local TDEE estimate (from UserProfileUpdated) — README §2 fact 3.
    @Column(name = "height_cm", precision = 5, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "activity_level", length = 40)
    private String activityLevel;

    // Derived locally (TDEE estimate adjusted by goal) — see README §2 fact 3 / R1.
    @Column(name = "energy_target_calories")
    private Integer energyTargetCalories;

    @Column(name = "protein_target_grams", precision = 10, scale = 2)
    private BigDecimal proteinTargetGrams;

    // The user's CALORIES goal. It takes priority over the energy target above.
    @Column(name = "calorie_goal_kcal")
    private Integer calorieGoalKcal;

    // Latest body metrics (from BodyMetricsRecorded).
    @Column(name = "latest_weight_kg", precision = 6, scale = 2)
    private BigDecimal latestWeightKg;

    @Column(name = "latest_body_fat_rate", precision = 5, scale = 2)
    private BigDecimal latestBodyFatRate;

    @Column(name = "body_metrics_recorded_at")
    private Instant bodyMetricsRecordedAt;

    // Preferences (from UserPreferencesUpdated).
    @Column(name = "workout_days_per_week")
    private Integer workoutDaysPerWeek;

    // The user's WORKOUT_FREQUENCY goal. It takes priority over the onboarding preference
    // above when both are present.
    @Column(name = "weekly_workout_goal_days")
    private Integer weeklyWorkoutGoalDays;

    @Column(name = "dietary_preference", length = 40)
    private String dietaryPreference;

    @Column(name = "focus_areas", length = 255)
    private String focusAreas;

    @Column(name = "equipment_inventory", length = 500)
    private String equipmentInventory;

    // Recent training summary (reused from WeeklyRecapGenerated — do not recompute).
    @Column(name = "recent_workout_count")
    private Integer recentWorkoutCount;

    @Column(name = "recent_workout_minutes")
    private Integer recentWorkoutMinutes;

    @Column(name = "recent_active_days")
    private Integer recentActiveDays;

    @Column(name = "recent_week_start")
    private LocalDate recentWeekStart;

    // Recent nutrition summary (from MealLogged rollups). today_date anchors the
    // running totals so a new day resets them instead of accumulating forever.
    @Column(name = "today_date")
    private LocalDate todayDate;

    @Column(name = "today_calories_consumed")
    private Integer todayCaloriesConsumed;

    @Column(name = "today_protein_grams", precision = 10, scale = 2)
    private BigDecimal todayProteinGrams;

    // Weekly food-group balance (from MealLogged.foodGroupCalories, EV-1).
    // nutrition_week_start anchors the running week (Monday, user timezone) so a
    // new week resets the accumulation; the JSON maps food-group code -> kcal.
    @Column(name = "nutrition_week_start")
    private LocalDate nutritionWeekStart;

    @Column(name = "week_food_group_calories", length = 1000)
    private String weekFoodGroupCaloriesJson;

    // Latest personal record (from PersonalRecordAchieved) — R6 celebrates it while fresh.
    @Column(name = "last_pr_exercise", length = 120)
    private String lastPrExercise;

    @Column(name = "last_pr_record_type", length = 40)
    private String lastPrRecordType;

    @Column(name = "last_pr_value", precision = 12, scale = 2)
    private BigDecimal lastPrValue;

    @Column(name = "last_pr_at")
    private Instant lastPrAt;

    // Consistency (from StreakMilestoneReached).
    @Column(name = "current_streak_days")
    private Integer currentStreakDays;

    @Column(name = "timezone", length = 80)
    private String timezone;

    // Change detection + freshness.
    @Column(name = "stale", nullable = false)
    private boolean stale = true;

    @Column(name = "context_hash", length = 64)
    private String contextHash;

    @Column(name = "last_assembled_at")
    private Instant lastAssembledAt;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getCurrentGoalType() {
        return currentGoalType;
    }

    public void setCurrentGoalType(String currentGoalType) {
        this.currentGoalType = currentGoalType;
    }

    public BigDecimal getCurrentGoalTargetValue() {
        return currentGoalTargetValue;
    }

    public void setCurrentGoalTargetValue(BigDecimal currentGoalTargetValue) {
        this.currentGoalTargetValue = currentGoalTargetValue;
    }

    public String getCurrentGoalTargetUnit() {
        return currentGoalTargetUnit;
    }

    public void setCurrentGoalTargetUnit(String currentGoalTargetUnit) {
        this.currentGoalTargetUnit = currentGoalTargetUnit;
    }

    public LocalDate getCurrentGoalTargetDate() {
        return currentGoalTargetDate;
    }

    public void setCurrentGoalTargetDate(LocalDate currentGoalTargetDate) {
        this.currentGoalTargetDate = currentGoalTargetDate;
    }

    public BigDecimal getGoalBaselineWeightKg() {
        return goalBaselineWeightKg;
    }

    public void setGoalBaselineWeightKg(BigDecimal goalBaselineWeightKg) {
        this.goalBaselineWeightKg = goalBaselineWeightKg;
    }

    public LocalDate getGoalBaselineOn() {
        return goalBaselineOn;
    }

    public void setGoalBaselineOn(LocalDate goalBaselineOn) {
        this.goalBaselineOn = goalBaselineOn;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
    }

    public String getActivityLevel() {
        return activityLevel;
    }

    public void setActivityLevel(String activityLevel) {
        this.activityLevel = activityLevel;
    }

    public Integer getEnergyTargetCalories() {
        return energyTargetCalories;
    }

    public void setEnergyTargetCalories(Integer energyTargetCalories) {
        this.energyTargetCalories = energyTargetCalories;
    }

    public BigDecimal getProteinTargetGrams() {
        return proteinTargetGrams;
    }

    public void setProteinTargetGrams(BigDecimal proteinTargetGrams) {
        this.proteinTargetGrams = proteinTargetGrams;
    }

    public Integer getCalorieGoalKcal() {
        return calorieGoalKcal;
    }

    public void setCalorieGoalKcal(Integer calorieGoalKcal) {
        this.calorieGoalKcal = calorieGoalKcal;
    }

    public BigDecimal getLatestWeightKg() {
        return latestWeightKg;
    }

    public void setLatestWeightKg(BigDecimal latestWeightKg) {
        this.latestWeightKg = latestWeightKg;
    }

    public BigDecimal getLatestBodyFatRate() {
        return latestBodyFatRate;
    }

    public void setLatestBodyFatRate(BigDecimal latestBodyFatRate) {
        this.latestBodyFatRate = latestBodyFatRate;
    }

    public Instant getBodyMetricsRecordedAt() {
        return bodyMetricsRecordedAt;
    }

    public void setBodyMetricsRecordedAt(Instant bodyMetricsRecordedAt) {
        this.bodyMetricsRecordedAt = bodyMetricsRecordedAt;
    }

    public Integer getWorkoutDaysPerWeek() {
        return workoutDaysPerWeek;
    }

    public void setWorkoutDaysPerWeek(Integer workoutDaysPerWeek) {
        this.workoutDaysPerWeek = workoutDaysPerWeek;
    }

    public Integer getWeeklyWorkoutGoalDays() {
        return weeklyWorkoutGoalDays;
    }

    public void setWeeklyWorkoutGoalDays(Integer weeklyWorkoutGoalDays) {
        this.weeklyWorkoutGoalDays = weeklyWorkoutGoalDays;
    }

    public String getDietaryPreference() {
        return dietaryPreference;
    }

    public void setDietaryPreference(String dietaryPreference) {
        this.dietaryPreference = dietaryPreference;
    }

    public String getFocusAreas() {
        return focusAreas;
    }

    public void setFocusAreas(String focusAreas) {
        this.focusAreas = focusAreas;
    }

    public String getEquipmentInventory() {
        return equipmentInventory;
    }

    public void setEquipmentInventory(String equipmentInventory) {
        this.equipmentInventory = equipmentInventory;
    }

    public Integer getRecentWorkoutCount() {
        return recentWorkoutCount;
    }

    public void setRecentWorkoutCount(Integer recentWorkoutCount) {
        this.recentWorkoutCount = recentWorkoutCount;
    }

    public Integer getRecentWorkoutMinutes() {
        return recentWorkoutMinutes;
    }

    public void setRecentWorkoutMinutes(Integer recentWorkoutMinutes) {
        this.recentWorkoutMinutes = recentWorkoutMinutes;
    }

    public Integer getRecentActiveDays() {
        return recentActiveDays;
    }

    public void setRecentActiveDays(Integer recentActiveDays) {
        this.recentActiveDays = recentActiveDays;
    }

    public LocalDate getRecentWeekStart() {
        return recentWeekStart;
    }

    public void setRecentWeekStart(LocalDate recentWeekStart) {
        this.recentWeekStart = recentWeekStart;
    }

    public LocalDate getTodayDate() {
        return todayDate;
    }

    public void setTodayDate(LocalDate todayDate) {
        this.todayDate = todayDate;
    }

    public Integer getTodayCaloriesConsumed() {
        return todayCaloriesConsumed;
    }

    public void setTodayCaloriesConsumed(Integer todayCaloriesConsumed) {
        this.todayCaloriesConsumed = todayCaloriesConsumed;
    }

    public BigDecimal getTodayProteinGrams() {
        return todayProteinGrams;
    }

    public void setTodayProteinGrams(BigDecimal todayProteinGrams) {
        this.todayProteinGrams = todayProteinGrams;
    }

    public LocalDate getNutritionWeekStart() {
        return nutritionWeekStart;
    }

    public void setNutritionWeekStart(LocalDate nutritionWeekStart) {
        this.nutritionWeekStart = nutritionWeekStart;
    }

    public String getWeekFoodGroupCaloriesJson() {
        return weekFoodGroupCaloriesJson;
    }

    public void setWeekFoodGroupCaloriesJson(String weekFoodGroupCaloriesJson) {
        this.weekFoodGroupCaloriesJson = weekFoodGroupCaloriesJson;
    }

    public String getLastPrExercise() {
        return lastPrExercise;
    }

    public void setLastPrExercise(String lastPrExercise) {
        this.lastPrExercise = lastPrExercise;
    }

    public String getLastPrRecordType() {
        return lastPrRecordType;
    }

    public void setLastPrRecordType(String lastPrRecordType) {
        this.lastPrRecordType = lastPrRecordType;
    }

    public BigDecimal getLastPrValue() {
        return lastPrValue;
    }

    public void setLastPrValue(BigDecimal lastPrValue) {
        this.lastPrValue = lastPrValue;
    }

    public Instant getLastPrAt() {
        return lastPrAt;
    }

    public void setLastPrAt(Instant lastPrAt) {
        this.lastPrAt = lastPrAt;
    }

    public Integer getCurrentStreakDays() {
        return currentStreakDays;
    }

    public void setCurrentStreakDays(Integer currentStreakDays) {
        this.currentStreakDays = currentStreakDays;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public boolean isStale() {
        return stale;
    }

    public void setStale(boolean stale) {
        this.stale = stale;
    }

    public String getContextHash() {
        return contextHash;
    }

    public void setContextHash(String contextHash) {
        this.contextHash = contextHash;
    }

    public Instant getLastAssembledAt() {
        return lastAssembledAt;
    }

    public void setLastAssembledAt(Instant lastAssembledAt) {
        this.lastAssembledAt = lastAssembledAt;
    }
}
