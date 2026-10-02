package com.fitsync.analytics.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "analytics_workout_contributions")
public class AnalyticsWorkoutContributionEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "calories_burned")
    private Integer caloriesBurned;

    @Column(name = "exercise_count")
    private Integer exerciseCount;

    @Column(name = "calories_estimated", nullable = false)
    private boolean caloriesEstimated;

    @Column(name = "last_applied_at", nullable = false)
    private Instant lastAppliedAt;

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
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

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Integer getCaloriesBurned() {
        return caloriesBurned;
    }

    public void setCaloriesBurned(Integer caloriesBurned) {
        this.caloriesBurned = caloriesBurned;
    }

    public Integer getExerciseCount() {
        return exerciseCount;
    }

    public void setExerciseCount(Integer exerciseCount) {
        this.exerciseCount = exerciseCount;
    }

    public boolean isCaloriesEstimated() {
        return caloriesEstimated;
    }

    public void setCaloriesEstimated(boolean caloriesEstimated) {
        this.caloriesEstimated = caloriesEstimated;
    }

    public Instant getLastAppliedAt() {
        return lastAppliedAt;
    }

    public void setLastAppliedAt(Instant lastAppliedAt) {
        this.lastAppliedAt = lastAppliedAt;
    }
}
