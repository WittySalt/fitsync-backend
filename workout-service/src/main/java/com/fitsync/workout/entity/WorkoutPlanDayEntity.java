package com.fitsync.workout.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;
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

/**
 * One day of a workout plan's week. A plan either has no days at all (a "flat" plan, whose
 * exercises hang directly off {@link WorkoutPlanEntity}) or has one day per scheduled day of the
 * week, each with its own kind, title, and exercises.
 *
 * <p>The {@link #exercises} collection here is a read-only inverse view of the exercises that
 * point at this day through {@code WorkoutPlanExerciseEntity.planDay}. Persistence for those rows
 * is owned by {@link WorkoutPlanEntity#getExercises()}, the same collection a flat plan already
 * uses, so a day's exercises are written and removed exactly the way a flat plan's are.
 */
@Entity
@Table(name = "workout_plan_days")
public class WorkoutPlanDayEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "plan_day_id", nullable = false)
    private UUID planDayId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private WorkoutPlanEntity plan;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private SessionKind kind;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "sport_activity", length = 40)
    private SportActivity sportActivity;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @OneToMany(mappedBy = "planDay")
    @OrderBy("sequence asc")
    private List<WorkoutPlanExerciseEntity> exercises = new ArrayList<>();

    public UUID getPlanDayId() { return planDayId; }
    public void setPlanDayId(UUID planDayId) { this.planDayId = planDayId; }
    public WorkoutPlanEntity getPlan() { return plan; }
    public void setPlan(WorkoutPlanEntity plan) { this.plan = plan; }
    public Integer getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public SessionKind getKind() { return kind; }
    public void setKind(SessionKind kind) { this.kind = kind; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public SportActivity getSportActivity() { return sportActivity; }
    public void setSportActivity(SportActivity sportActivity) { this.sportActivity = sportActivity; }
    public Integer getEstimatedDurationMinutes() { return estimatedDurationMinutes; }
    public void setEstimatedDurationMinutes(Integer estimatedDurationMinutes) { this.estimatedDurationMinutes = estimatedDurationMinutes; }
    public List<WorkoutPlanExerciseEntity> getExercises() { return exercises; }
    public void setExercises(List<WorkoutPlanExerciseEntity> exercises) { this.exercises = exercises; }
}
