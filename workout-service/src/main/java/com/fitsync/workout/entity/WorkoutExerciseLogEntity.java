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
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "workout_exercise_logs")
public class WorkoutExerciseLogEntity {

    @Id
    @Column(name = "exercise_log_id", nullable = false)
    private UUID exerciseLogId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private WorkoutSessionEntity session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private ExerciseEntity exercise;

    @Column(name = "exercise_name_snapshot", nullable = false, length = 120)
    private String exerciseNameSnapshot;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequence;

    @Column(name = "performed_sets")
    private Integer performedSets;

    @Column(name = "performed_reps")
    private Integer performedReps;

    @Column(name = "total_volume_kg", precision = 10, scale = 2)
    private BigDecimal totalVolumeKg;

    @Column(name = "distance_km", precision = 10, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "average_heart_rate")
    private Integer averageHeartRate;

    @Column(name = "max_heart_rate")
    private Integer maxHeartRate;

    @Column(name = "notes", length = 300)
    private String notes;

    @OneToMany(mappedBy = "exerciseLog", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("setNumber asc")
    @BatchSize(size = 100)
    private List<WorkoutExerciseSetLogEntity> sets = new ArrayList<>();

    public UUID getExerciseLogId() { return exerciseLogId; }
    public void setExerciseLogId(UUID exerciseLogId) { this.exerciseLogId = exerciseLogId; }
    public WorkoutSessionEntity getSession() { return session; }
    public void setSession(WorkoutSessionEntity session) { this.session = session; }
    public ExerciseEntity getExercise() { return exercise; }
    public void setExercise(ExerciseEntity exercise) { this.exercise = exercise; }
    public String getExerciseNameSnapshot() { return exerciseNameSnapshot; }
    public void setExerciseNameSnapshot(String exerciseNameSnapshot) { this.exerciseNameSnapshot = exerciseNameSnapshot; }
    public Integer getSequence() { return sequence; }
    public void setSequence(Integer sequence) { this.sequence = sequence; }
    public Integer getPerformedSets() { return performedSets; }
    public void setPerformedSets(Integer performedSets) { this.performedSets = performedSets; }
    public Integer getPerformedReps() { return performedReps; }
    public void setPerformedReps(Integer performedReps) { this.performedReps = performedReps; }
    public BigDecimal getTotalVolumeKg() { return totalVolumeKg; }
    public void setTotalVolumeKg(BigDecimal totalVolumeKg) { this.totalVolumeKg = totalVolumeKg; }
    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal distanceKm) { this.distanceKm = distanceKm; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public Integer getAverageHeartRate() { return averageHeartRate; }
    public void setAverageHeartRate(Integer averageHeartRate) { this.averageHeartRate = averageHeartRate; }
    public Integer getMaxHeartRate() { return maxHeartRate; }
    public void setMaxHeartRate(Integer maxHeartRate) { this.maxHeartRate = maxHeartRate; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public List<WorkoutExerciseSetLogEntity> getSets() { return sets; }
    public void setSets(List<WorkoutExerciseSetLogEntity> sets) { this.sets = sets; }
}
