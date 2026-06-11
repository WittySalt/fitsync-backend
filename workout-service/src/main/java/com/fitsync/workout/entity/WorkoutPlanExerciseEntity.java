package com.fitsync.workout.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "workout_plan_exercises")
public class WorkoutPlanExerciseEntity {

    @Id
    @Column(name = "plan_exercise_id", nullable = false)
    private UUID planExerciseId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private WorkoutPlanEntity plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private ExerciseEntity exercise;

    @Column(name = "exercise_name_snapshot", nullable = false, length = 120)
    private String exerciseNameSnapshot;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequence;

    @Column(name = "target_sets")
    private Integer targetSets;

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

    @Column(name = "notes", length = 300)
    private String notes;

    @OneToMany(mappedBy = "planExercise", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("setNumber asc")
    private List<WorkoutPlanExerciseSetTargetEntity> setTargets = new ArrayList<>();

    public UUID getPlanExerciseId() { return planExerciseId; }
    public void setPlanExerciseId(UUID planExerciseId) { this.planExerciseId = planExerciseId; }
    public WorkoutPlanEntity getPlan() { return plan; }
    public void setPlan(WorkoutPlanEntity plan) { this.plan = plan; }
    public ExerciseEntity getExercise() { return exercise; }
    public void setExercise(ExerciseEntity exercise) { this.exercise = exercise; }
    public String getExerciseNameSnapshot() { return exerciseNameSnapshot; }
    public void setExerciseNameSnapshot(String exerciseNameSnapshot) { this.exerciseNameSnapshot = exerciseNameSnapshot; }
    public Integer getSequence() { return sequence; }
    public void setSequence(Integer sequence) { this.sequence = sequence; }
    public Integer getTargetSets() { return targetSets; }
    public void setTargetSets(Integer targetSets) { this.targetSets = targetSets; }
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
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public List<WorkoutPlanExerciseSetTargetEntity> getSetTargets() { return setTargets; }
    public void setSetTargets(List<WorkoutPlanExerciseSetTargetEntity> setTargets) { this.setTargets = setTargets; }
}
