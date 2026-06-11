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
@Table(name = "workout_exercise_set_logs")
public class WorkoutExerciseSetLogEntity {

    @Id
    @Column(name = "set_log_id", nullable = false)
    private UUID setLogId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_log_id", nullable = false)
    private WorkoutExerciseLogEntity exerciseLog;

    @Column(name = "set_number", nullable = false)
    private Integer setNumber;

    @Column(name = "reps")
    private Integer reps;

    @Column(name = "weight_kg", precision = 10, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "distance_km", precision = 10, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "rest_seconds")
    private Integer restSeconds;

    @Column(name = "perceived_exertion")
    private Integer perceivedExertion;

    @Column(name = "notes", length = 300)
    private String notes;

    public UUID getSetLogId() { return setLogId; }
    public void setSetLogId(UUID setLogId) { this.setLogId = setLogId; }
    public WorkoutExerciseLogEntity getExerciseLog() { return exerciseLog; }
    public void setExerciseLog(WorkoutExerciseLogEntity exerciseLog) { this.exerciseLog = exerciseLog; }
    public Integer getSetNumber() { return setNumber; }
    public void setSetNumber(Integer setNumber) { this.setNumber = setNumber; }
    public Integer getReps() { return reps; }
    public void setReps(Integer reps) { this.reps = reps; }
    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal distanceKm) { this.distanceKm = distanceKm; }
    public Integer getRestSeconds() { return restSeconds; }
    public void setRestSeconds(Integer restSeconds) { this.restSeconds = restSeconds; }
    public Integer getPerceivedExertion() { return perceivedExertion; }
    public void setPerceivedExertion(Integer perceivedExertion) { this.perceivedExertion = perceivedExertion; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
