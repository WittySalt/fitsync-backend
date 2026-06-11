package com.fitsync.nutrition.entity;

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
@Table(name = "nutrition_targets")
public class NutritionTargetEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "calorie_goal_id")
    private UUID calorieGoalId;

    @Column(name = "calorie_target")
    private Integer calorieTarget;

    @Column(name = "calorie_target_date")
    private LocalDate calorieTargetDate;

    @Column(name = "protein_goal_id")
    private UUID proteinGoalId;

    @Column(name = "protein_target", precision = 10, scale = 2)
    private BigDecimal proteinTarget;

    @Column(name = "protein_target_date")
    private LocalDate proteinTargetDate;

    @Column(name = "carb_goal_id")
    private UUID carbGoalId;

    @Column(name = "carb_target_grams", precision = 10, scale = 2)
    private BigDecimal carbTargetGrams;

    @Column(name = "carb_target_date")
    private LocalDate carbTargetDate;

    @Column(name = "fat_goal_id")
    private UUID fatGoalId;

    @Column(name = "fat_target_grams", precision = 10, scale = 2)
    private BigDecimal fatTargetGrams;

    @Column(name = "fat_target_date")
    private LocalDate fatTargetDate;

    @Column(name = "projection_updated_at", nullable = false)
    private Instant projectionUpdatedAt;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getCalorieGoalId() {
        return calorieGoalId;
    }

    public void setCalorieGoalId(UUID calorieGoalId) {
        this.calorieGoalId = calorieGoalId;
    }

    public Integer getCalorieTarget() {
        return calorieTarget;
    }

    public void setCalorieTarget(Integer calorieTarget) {
        this.calorieTarget = calorieTarget;
    }

    public LocalDate getCalorieTargetDate() {
        return calorieTargetDate;
    }

    public void setCalorieTargetDate(LocalDate calorieTargetDate) {
        this.calorieTargetDate = calorieTargetDate;
    }

    public UUID getProteinGoalId() {
        return proteinGoalId;
    }

    public void setProteinGoalId(UUID proteinGoalId) {
        this.proteinGoalId = proteinGoalId;
    }

    public BigDecimal getProteinTarget() {
        return proteinTarget;
    }

    public void setProteinTarget(BigDecimal proteinTarget) {
        this.proteinTarget = proteinTarget;
    }

    public LocalDate getProteinTargetDate() {
        return proteinTargetDate;
    }

    public void setProteinTargetDate(LocalDate proteinTargetDate) {
        this.proteinTargetDate = proteinTargetDate;
    }

    public UUID getCarbGoalId() {
        return carbGoalId;
    }

    public void setCarbGoalId(UUID carbGoalId) {
        this.carbGoalId = carbGoalId;
    }

    public BigDecimal getCarbTargetGrams() {
        return carbTargetGrams;
    }

    public void setCarbTargetGrams(BigDecimal carbTargetGrams) {
        this.carbTargetGrams = carbTargetGrams;
    }

    public LocalDate getCarbTargetDate() {
        return carbTargetDate;
    }

    public void setCarbTargetDate(LocalDate carbTargetDate) {
        this.carbTargetDate = carbTargetDate;
    }

    public UUID getFatGoalId() {
        return fatGoalId;
    }

    public void setFatGoalId(UUID fatGoalId) {
        this.fatGoalId = fatGoalId;
    }

    public BigDecimal getFatTargetGrams() {
        return fatTargetGrams;
    }

    public void setFatTargetGrams(BigDecimal fatTargetGrams) {
        this.fatTargetGrams = fatTargetGrams;
    }

    public LocalDate getFatTargetDate() {
        return fatTargetDate;
    }

    public void setFatTargetDate(LocalDate fatTargetDate) {
        this.fatTargetDate = fatTargetDate;
    }

    public Instant getProjectionUpdatedAt() {
        return projectionUpdatedAt;
    }

    public void setProjectionUpdatedAt(Instant projectionUpdatedAt) {
        this.projectionUpdatedAt = projectionUpdatedAt;
    }
}
