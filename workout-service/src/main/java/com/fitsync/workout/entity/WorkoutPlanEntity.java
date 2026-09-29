package com.fitsync.workout.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import com.fitsync.workout.domain.GoalFocus;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutPlanStatus;
import com.fitsync.workout.domain.WorkoutType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "workout_plans")
public class WorkoutPlanEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal_focus", nullable = false, length = 40)
    private GoalFocus goalFocus;

    @Enumerated(EnumType.STRING)
    @Column(name = "workout_type", nullable = false, length = 30)
    private WorkoutType workoutType;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false, length = 30)
    private WorkoutDifficulty difficulty;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private WorkoutPlanStatus status;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence asc")
    private List<WorkoutPlanExerciseEntity> exercises = new ArrayList<>();

    /** Empty for a flat plan; one entry per scheduled day of the week otherwise. */
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayOfWeek asc")
    private List<WorkoutPlanDayEntity> days = new ArrayList<>();

    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public GoalFocus getGoalFocus() { return goalFocus; }
    public void setGoalFocus(GoalFocus goalFocus) { this.goalFocus = goalFocus; }
    public WorkoutType getWorkoutType() { return workoutType; }
    public void setWorkoutType(WorkoutType workoutType) { this.workoutType = workoutType; }
    public WorkoutDifficulty getDifficulty() { return difficulty; }
    public void setDifficulty(WorkoutDifficulty difficulty) { this.difficulty = difficulty; }
    public Integer getEstimatedDurationMinutes() { return estimatedDurationMinutes; }
    public void setEstimatedDurationMinutes(Integer estimatedDurationMinutes) { this.estimatedDurationMinutes = estimatedDurationMinutes; }
    public WorkoutPlanStatus getStatus() { return status; }
    public void setStatus(WorkoutPlanStatus status) { this.status = status; }
    public List<WorkoutPlanExerciseEntity> getExercises() { return exercises; }
    public void setExercises(List<WorkoutPlanExerciseEntity> exercises) { this.exercises = exercises; }
    public List<WorkoutPlanDayEntity> getDays() { return days; }
    public void setDays(List<WorkoutPlanDayEntity> days) { this.days = days; }
}
