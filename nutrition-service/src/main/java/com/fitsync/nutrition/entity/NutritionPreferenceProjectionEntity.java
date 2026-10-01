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
@Table(name = "nutrition_preference_projections")
public class NutritionPreferenceProjectionEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "preferred_units_code", length = 40)
    private String preferredUnitsCode;

    @Column(name = "dietary_preference_code", length = 40)
    private String dietaryPreferenceCode;

    @Column(name = "workout_days_per_week")
    private Integer workoutDaysPerWeek;

    @Column(name = "notifications_enabled")
    private boolean notificationsEnabled;

    @Column(name = "sex", length = 30)
    private String sex;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "weight_kg", precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "weight_measured_at")
    private Instant weightMeasuredAt;

    @Column(name = "projection_updated_at", nullable = false)
    private Instant projectionUpdatedAt;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getPreferredUnitsCode() {
        return preferredUnitsCode;
    }

    public void setPreferredUnitsCode(String preferredUnitsCode) {
        this.preferredUnitsCode = preferredUnitsCode;
    }

    public String getDietaryPreferenceCode() {
        return dietaryPreferenceCode;
    }

    public void setDietaryPreferenceCode(String dietaryPreferenceCode) {
        this.dietaryPreferenceCode = dietaryPreferenceCode;
    }

    public Integer getWorkoutDaysPerWeek() {
        return workoutDaysPerWeek;
    }

    public void setWorkoutDaysPerWeek(Integer workoutDaysPerWeek) {
        this.workoutDaysPerWeek = workoutDaysPerWeek;
    }

    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public Instant getWeightMeasuredAt() {
        return weightMeasuredAt;
    }

    public void setWeightMeasuredAt(Instant weightMeasuredAt) {
        this.weightMeasuredAt = weightMeasuredAt;
    }

    public Instant getProjectionUpdatedAt() {
        return projectionUpdatedAt;
    }

    public void setProjectionUpdatedAt(Instant projectionUpdatedAt) {
        this.projectionUpdatedAt = projectionUpdatedAt;
    }
}
