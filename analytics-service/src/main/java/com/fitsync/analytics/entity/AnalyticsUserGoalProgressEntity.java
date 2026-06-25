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
@Table(name = "analytics_user_goal_progress")
public class AnalyticsUserGoalProgressEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "goal_id", nullable = false)
    private UUID goalId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "goal_type", nullable = false, length = 40)
    private String goalType;

    @Column(name = "goal_status", nullable = false, length = 30)
    private String goalStatus;

    @Column(name = "target_value", precision = 10, scale = 2)
    private BigDecimal targetValue;

    @Column(name = "target_unit", length = 20)
    private String targetUnit;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "baseline_value", precision = 10, scale = 2)
    private BigDecimal baselineValue;

    @Column(name = "current_value", precision = 10, scale = 2)
    private BigDecimal currentValue;

    @Column(name = "progress_ratio", precision = 6, scale = 4)
    private BigDecimal progressRatio;

    @Column(name = "days_remaining")
    private Integer daysRemaining;

    @Column(name = "on_track", nullable = false)
    private boolean onTrack;

    @Column(name = "last_evaluated_at", nullable = false)
    private Instant lastEvaluatedAt;

    public UUID getGoalId() {
        return goalId;
    }

    public void setGoalId(UUID goalId) {
        this.goalId = goalId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getGoalType() {
        return goalType;
    }

    public void setGoalType(String goalType) {
        this.goalType = goalType;
    }

    public String getGoalStatus() {
        return goalStatus;
    }

    public void setGoalStatus(String goalStatus) {
        this.goalStatus = goalStatus;
    }

    public BigDecimal getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(BigDecimal targetValue) {
        this.targetValue = targetValue;
    }

    public String getTargetUnit() {
        return targetUnit;
    }

    public void setTargetUnit(String targetUnit) {
        this.targetUnit = targetUnit;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    public BigDecimal getBaselineValue() {
        return baselineValue;
    }

    public void setBaselineValue(BigDecimal baselineValue) {
        this.baselineValue = baselineValue;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(BigDecimal currentValue) {
        this.currentValue = currentValue;
    }

    public BigDecimal getProgressRatio() {
        return progressRatio;
    }

    public void setProgressRatio(BigDecimal progressRatio) {
        this.progressRatio = progressRatio;
    }

    public Integer getDaysRemaining() {
        return daysRemaining;
    }

    public void setDaysRemaining(Integer daysRemaining) {
        this.daysRemaining = daysRemaining;
    }

    public boolean isOnTrack() {
        return onTrack;
    }

    public void setOnTrack(boolean onTrack) {
        this.onTrack = onTrack;
    }

    public Instant getLastEvaluatedAt() {
        return lastEvaluatedAt;
    }

    public void setLastEvaluatedAt(Instant lastEvaluatedAt) {
        this.lastEvaluatedAt = lastEvaluatedAt;
    }
}
