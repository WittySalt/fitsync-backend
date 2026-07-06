package com.fitsync.analytics.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "analytics_user_daily_nutrition")
@IdClass(AnalyticsUserActivityDateKey.class)
public class AnalyticsUserDailyNutritionEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "meal_count", nullable = false)
    private Integer mealCount;

    @Column(name = "total_calories_consumed")
    private Integer totalCaloriesConsumed;

    @Column(name = "total_protein_grams", precision = 10, scale = 2)
    private BigDecimal totalProteinGrams;

    @Column(name = "total_carbs_grams", precision = 10, scale = 2)
    private BigDecimal totalCarbsGrams;

    @Column(name = "total_fat_grams", precision = 10, scale = 2)
    private BigDecimal totalFatGrams;

    @Column(name = "calorie_target")
    private Integer calorieTarget;

    @Column(name = "protein_target_grams", precision = 10, scale = 2)
    private BigDecimal proteinTargetGrams;

    @Column(name = "calorie_adherence_ratio", precision = 6, scale = 4)
    private BigDecimal calorieAdherenceRatio;

    @Column(name = "macro_adherence_score", precision = 6, scale = 4)
    private BigDecimal macroAdherenceScore;

    @Column(name = "projection_updated_at", nullable = false)
    private Instant projectionUpdatedAt;

    // Daily food-group energy aggregate, encoded as "CODE=kcal;CODE=kcal" (nullable). Delta-maintained
    // from per-meal contributions; lets analytics/recommendation read food-group balance ("肉多菜少"). AN-1.
    @Column(name = "food_group_calories", length = 600)
    private String foodGroupCalories;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public LocalDate getActivityDate() {
        return activityDate;
    }

    public void setActivityDate(LocalDate activityDate) {
        this.activityDate = activityDate;
    }

    public Integer getMealCount() {
        return mealCount;
    }

    public void setMealCount(Integer mealCount) {
        this.mealCount = mealCount;
    }

    public Integer getTotalCaloriesConsumed() {
        return totalCaloriesConsumed;
    }

    public void setTotalCaloriesConsumed(Integer totalCaloriesConsumed) {
        this.totalCaloriesConsumed = totalCaloriesConsumed;
    }

    public BigDecimal getTotalProteinGrams() {
        return totalProteinGrams;
    }

    public void setTotalProteinGrams(BigDecimal totalProteinGrams) {
        this.totalProteinGrams = totalProteinGrams;
    }

    public BigDecimal getTotalCarbsGrams() {
        return totalCarbsGrams;
    }

    public void setTotalCarbsGrams(BigDecimal totalCarbsGrams) {
        this.totalCarbsGrams = totalCarbsGrams;
    }

    public BigDecimal getTotalFatGrams() {
        return totalFatGrams;
    }

    public void setTotalFatGrams(BigDecimal totalFatGrams) {
        this.totalFatGrams = totalFatGrams;
    }

    public Integer getCalorieTarget() {
        return calorieTarget;
    }

    public void setCalorieTarget(Integer calorieTarget) {
        this.calorieTarget = calorieTarget;
    }

    public BigDecimal getProteinTargetGrams() {
        return proteinTargetGrams;
    }

    public void setProteinTargetGrams(BigDecimal proteinTargetGrams) {
        this.proteinTargetGrams = proteinTargetGrams;
    }

    public BigDecimal getCalorieAdherenceRatio() {
        return calorieAdherenceRatio;
    }

    public void setCalorieAdherenceRatio(BigDecimal calorieAdherenceRatio) {
        this.calorieAdherenceRatio = calorieAdherenceRatio;
    }

    public BigDecimal getMacroAdherenceScore() {
        return macroAdherenceScore;
    }

    public void setMacroAdherenceScore(BigDecimal macroAdherenceScore) {
        this.macroAdherenceScore = macroAdherenceScore;
    }

    public Instant getProjectionUpdatedAt() {
        return projectionUpdatedAt;
    }

    public void setProjectionUpdatedAt(Instant projectionUpdatedAt) {
        this.projectionUpdatedAt = projectionUpdatedAt;
    }

    public String getFoodGroupCalories() {
        return foodGroupCalories;
    }

    public void setFoodGroupCalories(String foodGroupCalories) {
        this.foodGroupCalories = foodGroupCalories;
    }
}
