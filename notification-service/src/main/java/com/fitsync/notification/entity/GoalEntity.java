package com.fitsync.notification.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_goals")
public class GoalEntity {

    @Id
    @Column(name = "goal_id", nullable = false)
    private UUID goalId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "goal_type", nullable = false, length = 40)
    private String goalType;

    @Column(name = "target_value", precision = 10, scale = 2)
    private BigDecimal targetValue;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "start_weight_kg", precision = 6, scale = 2)
    private BigDecimal startWeightKg;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GoalEntity() {
    }

    public GoalEntity(UUID goalId, UUID userId, Instant startedAt) {
        this.goalId = goalId;
        this.userId = userId;
        this.startedAt = startedAt;
        this.updatedAt = startedAt;
    }

    public UUID getGoalId() { return goalId; }
    public UUID getUserId() { return userId; }
    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }
    public BigDecimal getTargetValue() { return targetValue; }
    public void setTargetValue(BigDecimal targetValue) { this.targetValue = targetValue; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getStartWeightKg() { return startWeightKg; }
    public void setStartWeightKg(BigDecimal startWeightKg) { this.startWeightKg = startWeightKg; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
