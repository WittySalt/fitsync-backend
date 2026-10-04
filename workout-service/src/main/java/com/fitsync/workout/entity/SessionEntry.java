package com.fitsync.workout.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.EntryType;
import com.fitsync.workout.domain.SegmentKind;
import com.fitsync.workout.domain.WorkoutSetStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

/**
 * One entry of a workout session, in order: a SET entry is a move with its logged sets, a SEGMENT
 * entry is a stretch of time with its planned and actual seconds. A SEGMENT entry has no exercise and
 * no sets; its name snapshot holds its label so the column stays filled.
 */
@Entity
@Table(name = "workout_session_entries")
public class SessionEntry {

    @Id
    @Column(name = "exercise_log_id", nullable = false)
    private UUID exerciseLogId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private WorkoutSessionEntity session;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 8)
    private EntryType entryType = EntryType.SET;

    /** The exercise of a SET entry; null for a SEGMENT entry. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id")
    private ExerciseEntity exercise;

    @Column(name = "exercise_name_snapshot", nullable = false, length = 120)
    private String exerciseNameSnapshot;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequence;

    /**
     * The order of the entry among the entries of its type: for a SET entry its sequence, for a SEGMENT
     * entry its place in the timeline. Segments number their sequence from 1001, so the moves keep
     * their own numbers under the unique order index.
     */
    @Column(name = "position", nullable = false)
    private Integer position;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 10)
    private SegmentKind kind;

    @Column(name = "label", length = 40)
    private String label;

    @Column(name = "planned_seconds")
    private Integer plannedSeconds;

    /** The seconds a segment actually ran; empty until it is done. */
    @Column(name = "actual_seconds")
    private Integer actualSeconds;

    /** When a segment began; empty until it is started. */
    @Column(name = "started_at")
    private Instant startedAt;

    /** The session's local date. */
    @Column(name = "logged_on")
    private LocalDate loggedOn;

    /** Whether a segment is pending, done or skipped; empty for a SET entry, whose sets carry it. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private WorkoutSetStatus status;

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

    /** Seconds one rep takes when the exercise screen counts along, or null for the default of its kind. */
    @Column(name = "tempo_seconds", precision = 3, scale = 1)
    private BigDecimal tempoSeconds;

    @OneToMany(mappedBy = "exerciseLog", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("setNumber asc")
    @BatchSize(size = 100)
    private List<WorkoutExerciseSetLogEntity> sets = new ArrayList<>();

    /** True for a move measured in sets; false for a time segment. */
    public boolean isSet() {
        return entryType != EntryType.SEGMENT;
    }

    public UUID getExerciseLogId() { return exerciseLogId; }
    public void setExerciseLogId(UUID exerciseLogId) { this.exerciseLogId = exerciseLogId; }
    public WorkoutSessionEntity getSession() { return session; }
    public void setSession(WorkoutSessionEntity session) { this.session = session; }
    public EntryType getEntryType() { return entryType; }
    public void setEntryType(EntryType entryType) { this.entryType = entryType == null ? EntryType.SET : entryType; }
    public ExerciseEntity getExercise() { return exercise; }
    public void setExercise(ExerciseEntity exercise) { this.exercise = exercise; }
    public String getExerciseNameSnapshot() { return exerciseNameSnapshot; }
    public void setExerciseNameSnapshot(String exerciseNameSnapshot) { this.exerciseNameSnapshot = exerciseNameSnapshot; }
    public Integer getSequence() { return sequence; }
    /** Sets the order; the position follows it unless set after. */
    public void setSequence(Integer sequence) {
        this.sequence = sequence;
        this.position = sequence;
    }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
    public SegmentKind getKind() { return kind; }
    public void setKind(SegmentKind kind) { this.kind = kind; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Integer getPlannedSeconds() { return plannedSeconds; }
    public void setPlannedSeconds(Integer plannedSeconds) { this.plannedSeconds = plannedSeconds; }
    public Integer getActualSeconds() { return actualSeconds; }
    public void setActualSeconds(Integer actualSeconds) { this.actualSeconds = actualSeconds; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public LocalDate getLoggedOn() { return loggedOn; }
    public void setLoggedOn(LocalDate loggedOn) { this.loggedOn = loggedOn; }
    public WorkoutSetStatus getStatus() { return status; }
    public void setStatus(WorkoutSetStatus status) { this.status = status; }
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
    public BigDecimal getTempoSeconds() { return tempoSeconds; }
    public void setTempoSeconds(BigDecimal tempoSeconds) { this.tempoSeconds = tempoSeconds; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public List<WorkoutExerciseSetLogEntity> getSets() { return sets; }
    public void setSets(List<WorkoutExerciseSetLogEntity> sets) { this.sets = sets; }
}
