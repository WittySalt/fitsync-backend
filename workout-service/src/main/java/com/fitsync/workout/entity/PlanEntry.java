package com.fitsync.workout.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.EntryType;
import com.fitsync.workout.domain.SegmentKind;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One entry of a workout plan, in order: a SET entry is a move with its set targets, a SEGMENT entry
 * is a stretch of time on a cardio or sport day. A SEGMENT entry has no exercise; its name snapshot
 * holds its label so the column stays filled.
 */
@Entity
@Table(name = "workout_plan_entries")
public class PlanEntry {

    @Id
    @Column(name = "plan_exercise_id", nullable = false)
    private UUID planExerciseId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private WorkoutPlanEntity plan;

    /**
     * The plan day this entry belongs to, or null when the plan is flat (has no week). Set once
     * at creation and never reassigned: an update replaces the whole week rather than moving an
     * entry between days.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_day_id")
    private WorkoutPlanDayEntity planDay;

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

    /** The length of a segment, or of one set of a time-based move. */
    @Column(name = "planned_seconds")
    private Integer plannedSeconds;

    /** How many times a segment repeats; segments only. */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "rounds")
    private Integer rounds;

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

    /** Seconds one rep takes when the exercise screen counts along, or null for the default of its kind. */
    @Column(name = "tempo_seconds", precision = 3, scale = 1)
    private BigDecimal tempoSeconds;

    @OneToMany(mappedBy = "planExercise", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("setNumber asc")
    @BatchSize(size = 100)
    private List<WorkoutPlanExerciseSetTargetEntity> setTargets = new ArrayList<>();

    /** True for a move measured in sets; false for a time segment. */
    public boolean isSet() {
        return entryType != EntryType.SEGMENT;
    }

    /** The id of the plan day this entry belongs to, or null when the plan is flat. */
    public UUID planDayId() {
        return planDay == null ? null : planDay.getPlanDayId();
    }

    public UUID getPlanExerciseId() { return planExerciseId; }
    public void setPlanExerciseId(UUID planExerciseId) { this.planExerciseId = planExerciseId; }
    public WorkoutPlanEntity getPlan() { return plan; }
    public void setPlan(WorkoutPlanEntity plan) { this.plan = plan; }
    public WorkoutPlanDayEntity getPlanDay() { return planDay; }
    public void setPlanDay(WorkoutPlanDayEntity planDay) { this.planDay = planDay; }
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
    public Integer getRounds() { return rounds; }
    public void setRounds(Integer rounds) { this.rounds = rounds; }
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
    public BigDecimal getTempoSeconds() { return tempoSeconds; }
    public void setTempoSeconds(BigDecimal tempoSeconds) { this.tempoSeconds = tempoSeconds; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public List<WorkoutPlanExerciseSetTargetEntity> getSetTargets() { return setTargets; }
    public void setSetTargets(List<WorkoutPlanExerciseSetTargetEntity> setTargets) { this.setTargets = setTargets; }
}
