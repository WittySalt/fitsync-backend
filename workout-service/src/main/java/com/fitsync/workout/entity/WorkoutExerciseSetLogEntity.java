package com.fitsync.workout.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fitsync.workout.domain.WorkoutSetStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
    private SessionEntry exerciseLog;

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

    @Column(name = "logged", nullable = false)
    private boolean logged;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkoutSetStatus status = WorkoutSetStatus.PENDING;

    /** When the set was last marked done or skipped; empty while it is still pending. */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** The rest the person actually took after this set; empty when it was not timed. */
    @Column(name = "rest_taken_seconds")
    private Integer restTakenSeconds;

    public UUID getSetLogId() { return setLogId; }
    public void setSetLogId(UUID setLogId) { this.setLogId = setLogId; }
    public SessionEntry getExerciseLog() { return exerciseLog; }
    public void setExerciseLog(SessionEntry exerciseLog) { this.exerciseLog = exerciseLog; }
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
    public boolean isLogged() { return logged; }
    public void setLogged(boolean logged) {
        this.logged = logged;
        this.status = logged ? WorkoutSetStatus.DONE : WorkoutSetStatus.PENDING;
    }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Integer getRestTakenSeconds() { return restTakenSeconds; }
    public void setRestTakenSeconds(Integer restTakenSeconds) { this.restTakenSeconds = restTakenSeconds; }
    public WorkoutSetStatus getStatus() { return status; }
    public void setStatus(WorkoutSetStatus status) {
        this.status = status == null ? WorkoutSetStatus.PENDING : status;
        this.logged = this.status == WorkoutSetStatus.DONE;
    }
}
