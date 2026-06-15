package com.fitsync.analytics.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "analytics_meal_contributions")
public class AnalyticsMealContributionEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "meal_id", nullable = false)
    private UUID mealId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "total_calories")
    private Integer totalCalories;

    @Column(name = "total_protein_grams", precision = 10, scale = 2)
    private BigDecimal totalProteinGrams;

    @Column(name = "total_carbs_grams", precision = 10, scale = 2)
    private BigDecimal totalCarbsGrams;

    @Column(name = "total_fat_grams", precision = 10, scale = 2)
    private BigDecimal totalFatGrams;

    @Column(name = "item_count", nullable = false)
    private Integer itemCount;

    @Column(name = "last_applied_at", nullable = false)
    private Instant lastAppliedAt;

    public UUID getMealId() {
        return mealId;
    }

    public void setMealId(UUID mealId) {
        this.mealId = mealId;
    }

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

    public Integer getTotalCalories() {
        return totalCalories;
    }

    public void setTotalCalories(Integer totalCalories) {
        this.totalCalories = totalCalories;
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

    public Integer getItemCount() {
        return itemCount;
    }

    public void setItemCount(Integer itemCount) {
        this.itemCount = itemCount;
    }

    public Instant getLastAppliedAt() {
        return lastAppliedAt;
    }

    public void setLastAppliedAt(Instant lastAppliedAt) {
        this.lastAppliedAt = lastAppliedAt;
    }
}
