package com.fitsync.workout.entity;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "workout_plan_exercise_set_targets")
public class WorkoutPlanExerciseSetTargetEntity {

    @Id
    @Column(name = "set_target_id", nullable = false)
    private UUID setTargetId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_exercise_id", nullable = false)
    private PlanEntry planExercise;

    @Column(name = "set_number", nullable = false)
    private Integer setNumber;

    @Column(name = "target_reps")
    private Integer targetReps;

    @Column(name = "target_weight_kg", precision = 10, scale = 2)
    private BigDecimal targetWeightKg;

    @Column(name = "target_duration_seconds")
    private Integer targetDurationSeconds;

    @Column(name = "target_distance_km", precision = 10, scale = 2)
    private BigDecimal targetDistanceKm;

    @Column(name = "rest_seconds")
    private Integer restSeconds;

    public UUID getSetTargetId() { return setTargetId; }
    public void setSetTargetId(UUID setTargetId) { this.setTargetId = setTargetId; }
    public PlanEntry getPlanExercise() { return planExercise; }
    public void setPlanExercise(PlanEntry planExercise) { this.planExercise = planExercise; }
    public Integer getSetNumber() { return setNumber; }
    public void setSetNumber(Integer setNumber) { this.setNumber = setNumber; }
    public Integer getTargetReps() { return targetReps; }
    public void setTargetReps(Integer targetReps) { this.targetReps = targetReps; }
    public BigDecimal getTargetWeightKg() { return targetWeightKg; }
    public void setTargetWeightKg(BigDecimal targetWeightKg) { this.targetWeightKg = targetWeightKg; }
    public Integer getTargetDurationSeconds() { return targetDurationSeconds; }
    public void setTargetDurationSeconds(Integer targetDurationSeconds) { this.targetDurationSeconds = targetDurationSeconds; }
    public BigDecimal getTargetDistanceKm() { return targetDistanceKm; }
    public void setTargetDistanceKm(BigDecimal targetDistanceKm) { this.targetDistanceKm = targetDistanceKm; }
    public Integer getRestSeconds() { return restSeconds; }
    public void setRestSeconds(Integer restSeconds) { this.restSeconds = restSeconds; }
}
