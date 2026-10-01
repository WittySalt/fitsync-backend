package com.fitsync.workout.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSource;
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
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "workout_sessions")
// A deleted session is only marked; every query leaves it out. See V15.
@SQLRestriction("deleted_at is null")
public class WorkoutSessionEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "workout_type", nullable = false, length = 30)
    private WorkoutType workoutType;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private SessionKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "sport_activity", length = 40)
    private SportActivity sportActivity;

    @Column(name = "plan_day_id")
    private UUID planDayId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private WorkoutSessionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private WorkoutSource source;

    @Column(name = "external_id", length = 120)
    private String externalId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "calories_burned")
    private Integer caloriesBurned;

    @Column(name = "perceived_exertion")
    private Integer perceivedExertion;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "distance_km", precision = 7, scale = 2)
    private BigDecimal distanceKm;

    /** Set when the server closed this session because it was left open; empty otherwise. */
    @Column(name = "auto_closed_at")
    private Instant autoClosedAt;

    /** When the session ended, completed or cancelled; empty while it is in progress. */
    @Column(name = "ended_at")
    private Instant endedAt;

    /** The calories were estimated by the server, not entered by the person. */
    @Column(name = "calories_estimated", nullable = false)
    private boolean caloriesEstimated;

    /** The effort was estimated by the server, not chosen by the person. */
    @Column(name = "effort_estimated", nullable = false)
    private boolean effortEstimated;

    /** Set when the person deleted the session; the row is kept and left out of every read. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence asc")
    private List<WorkoutExerciseLogEntity> exercises = new ArrayList<>();

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public WorkoutType getWorkoutType() { return workoutType; }
    public void setWorkoutType(WorkoutType workoutType) { this.workoutType = workoutType; }
    public SessionKind getKind() { return kind; }
    public void setKind(SessionKind kind) { this.kind = kind; }
    public SportActivity getSportActivity() { return sportActivity; }
    public void setSportActivity(SportActivity sportActivity) { this.sportActivity = sportActivity; }
    public UUID getPlanDayId() { return planDayId; }
    public void setPlanDayId(UUID planDayId) { this.planDayId = planDayId; }
    public WorkoutSessionStatus getStatus() { return status; }
    public void setStatus(WorkoutSessionStatus status) { this.status = status; }
    public WorkoutSource getSource() { return source; }
    public void setSource(WorkoutSource source) { this.source = source; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public Integer getCaloriesBurned() { return caloriesBurned; }
    public void setCaloriesBurned(Integer caloriesBurned) { this.caloriesBurned = caloriesBurned; }
    public Integer getPerceivedExertion() { return perceivedExertion; }
    public void setPerceivedExertion(Integer perceivedExertion) { this.perceivedExertion = perceivedExertion; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal distanceKm) { this.distanceKm = distanceKm; }
    public Instant getAutoClosedAt() { return autoClosedAt; }
    public void setAutoClosedAt(Instant autoClosedAt) { this.autoClosedAt = autoClosedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public boolean isCaloriesEstimated() { return caloriesEstimated; }
    public void setCaloriesEstimated(boolean caloriesEstimated) { this.caloriesEstimated = caloriesEstimated; }
    public boolean isEffortEstimated() { return effortEstimated; }
    public void setEffortEstimated(boolean effortEstimated) { this.effortEstimated = effortEstimated; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public List<WorkoutExerciseLogEntity> getExercises() { return exercises; }
    public void setExercises(List<WorkoutExerciseLogEntity> exercises) { this.exercises = exercises; }
}
