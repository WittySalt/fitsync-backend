package com.fitsync.analytics.entity;

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
@Table(name = "analytics_user_daily_workout")
@IdClass(AnalyticsUserActivityDateKey.class)
public class AnalyticsUserDailyWorkoutEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "completed_workout_count", nullable = false)
    private Integer completedWorkoutCount;

    @Column(name = "total_workout_minutes", nullable = false)
    private Integer totalWorkoutMinutes;

    @Column(name = "total_calories_burned", nullable = false)
    private Integer totalCaloriesBurned;

    // Part of total_calories_burned that the server estimated; the day counts as estimated when above zero.
    @Column(name = "estimated_calories_burned", nullable = false)
    private Integer estimatedCaloriesBurned = 0;

    @Column(name = "total_exercise_count", nullable = false)
    private Integer totalExerciseCount;

    @Column(name = "rolling_7_day_workout_count", nullable = false)
    private Integer rolling7DayWorkoutCount;

    @Column(name = "rolling_28_day_workout_count", nullable = false)
    private Integer rolling28DayWorkoutCount;

    @Column(name = "projection_updated_at", nullable = false)
    private Instant projectionUpdatedAt;

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

    public Integer getCompletedWorkoutCount() {
        return completedWorkoutCount;
    }

    public void setCompletedWorkoutCount(Integer completedWorkoutCount) {
        this.completedWorkoutCount = completedWorkoutCount;
    }

    public Integer getTotalWorkoutMinutes() {
        return totalWorkoutMinutes;
    }

    public void setTotalWorkoutMinutes(Integer totalWorkoutMinutes) {
        this.totalWorkoutMinutes = totalWorkoutMinutes;
    }

    public Integer getTotalCaloriesBurned() {
        return totalCaloriesBurned;
    }

    public void setTotalCaloriesBurned(Integer totalCaloriesBurned) {
        this.totalCaloriesBurned = totalCaloriesBurned;
    }

    public Integer getEstimatedCaloriesBurned() {
        return estimatedCaloriesBurned;
    }

    public void setEstimatedCaloriesBurned(Integer estimatedCaloriesBurned) {
        this.estimatedCaloriesBurned = estimatedCaloriesBurned;
    }

    public Integer getTotalExerciseCount() {
        return totalExerciseCount;
    }

    public void setTotalExerciseCount(Integer totalExerciseCount) {
        this.totalExerciseCount = totalExerciseCount;
    }

    public Integer getRolling7DayWorkoutCount() {
        return rolling7DayWorkoutCount;
    }

    public void setRolling7DayWorkoutCount(Integer rolling7DayWorkoutCount) {
        this.rolling7DayWorkoutCount = rolling7DayWorkoutCount;
    }

    public Integer getRolling28DayWorkoutCount() {
        return rolling28DayWorkoutCount;
    }

    public void setRolling28DayWorkoutCount(Integer rolling28DayWorkoutCount) {
        this.rolling28DayWorkoutCount = rolling28DayWorkoutCount;
    }

    public Instant getProjectionUpdatedAt() {
        return projectionUpdatedAt;
    }

    public void setProjectionUpdatedAt(Instant projectionUpdatedAt) {
        this.projectionUpdatedAt = projectionUpdatedAt;
    }
}
