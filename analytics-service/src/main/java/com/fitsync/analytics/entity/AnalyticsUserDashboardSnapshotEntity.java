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
@Table(name = "analytics_user_dashboard_snapshot")
public class AnalyticsUserDashboardSnapshotEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "timezone", length = 80)
    private String timezone;

    @Column(name = "latest_weight_kg", precision = 6, scale = 2)
    private BigDecimal latestWeightKg;

    @Column(name = "latest_body_fat_rate", precision = 5, scale = 2)
    private BigDecimal latestBodyFatRate;

    @Column(name = "bmi", precision = 6, scale = 2)
    private BigDecimal bmi;

    @Column(name = "weight_change_30d", precision = 6, scale = 2)
    private BigDecimal weightChange30d;

    @Column(name = "latest_waist_cm", precision = 6, scale = 2)
    private BigDecimal latestWaistCm;

    @Column(name = "waist_measured_on")
    private LocalDate waistMeasuredOn;

    @Column(name = "today_workout_count", nullable = false)
    private Integer todayWorkoutCount;

    @Column(name = "week_workout_count", nullable = false)
    private Integer weekWorkoutCount;

    @Column(name = "week_workout_minutes", nullable = false)
    private Integer weekWorkoutMinutes;

    @Column(name = "active_streak_days", nullable = false)
    private Integer activeStreakDays;

    @Column(name = "today_calories_consumed")
    private Integer todayCaloriesConsumed;

    @Column(name = "today_calories_target")
    private Integer todayCaloriesTarget;

    @Column(name = "today_protein_grams", precision = 10, scale = 2)
    private BigDecimal todayProteinGrams;

    @Column(name = "today_protein_target_grams", precision = 10, scale = 2)
    private BigDecimal todayProteinTargetGrams;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public BigDecimal getLatestWeightKg() {
        return latestWeightKg;
    }

    public void setLatestWeightKg(BigDecimal latestWeightKg) {
        this.latestWeightKg = latestWeightKg;
    }

    public BigDecimal getLatestWaistCm() {
        return latestWaistCm;
    }

    public void setLatestWaistCm(BigDecimal latestWaistCm) {
        this.latestWaistCm = latestWaistCm;
    }

    public LocalDate getWaistMeasuredOn() {
        return waistMeasuredOn;
    }

    public void setWaistMeasuredOn(LocalDate waistMeasuredOn) {
        this.waistMeasuredOn = waistMeasuredOn;
    }

    public BigDecimal getLatestBodyFatRate() {
        return latestBodyFatRate;
    }

    public void setLatestBodyFatRate(BigDecimal latestBodyFatRate) {
        this.latestBodyFatRate = latestBodyFatRate;
    }

    public BigDecimal getBmi() {
        return bmi;
    }

    public void setBmi(BigDecimal bmi) {
        this.bmi = bmi;
    }

    public BigDecimal getWeightChange30d() {
        return weightChange30d;
    }

    public void setWeightChange30d(BigDecimal weightChange30d) {
        this.weightChange30d = weightChange30d;
    }

    public Integer getTodayWorkoutCount() {
        return todayWorkoutCount;
    }

    public void setTodayWorkoutCount(Integer todayWorkoutCount) {
        this.todayWorkoutCount = todayWorkoutCount;
    }

    public Integer getWeekWorkoutCount() {
        return weekWorkoutCount;
    }

    public void setWeekWorkoutCount(Integer weekWorkoutCount) {
        this.weekWorkoutCount = weekWorkoutCount;
    }

    public Integer getWeekWorkoutMinutes() {
        return weekWorkoutMinutes;
    }

    public void setWeekWorkoutMinutes(Integer weekWorkoutMinutes) {
        this.weekWorkoutMinutes = weekWorkoutMinutes;
    }

    public Integer getActiveStreakDays() {
        return activeStreakDays;
    }

    public void setActiveStreakDays(Integer activeStreakDays) {
        this.activeStreakDays = activeStreakDays;
    }

    public Integer getTodayCaloriesConsumed() {
        return todayCaloriesConsumed;
    }

    public void setTodayCaloriesConsumed(Integer todayCaloriesConsumed) {
        this.todayCaloriesConsumed = todayCaloriesConsumed;
    }

    public Integer getTodayCaloriesTarget() {
        return todayCaloriesTarget;
    }

    public void setTodayCaloriesTarget(Integer todayCaloriesTarget) {
        this.todayCaloriesTarget = todayCaloriesTarget;
    }

    public BigDecimal getTodayProteinGrams() {
        return todayProteinGrams;
    }

    public void setTodayProteinGrams(BigDecimal todayProteinGrams) {
        this.todayProteinGrams = todayProteinGrams;
    }

    public BigDecimal getTodayProteinTargetGrams() {
        return todayProteinTargetGrams;
    }

    public void setTodayProteinTargetGrams(BigDecimal todayProteinTargetGrams) {
        this.todayProteinTargetGrams = todayProteinTargetGrams;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }
}
