package com.fitsync.workout.entity;

import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import com.fitsync.workout.domain.MuscleGroup;
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
@Table(name = "exercise_secondary_muscles")
public class ExerciseSecondaryMuscleEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "secondary_muscle_id", nullable = false)
    private UUID secondaryMuscleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private ExerciseEntity exercise;

    @Enumerated(EnumType.STRING)
    @Column(name = "muscle_group", nullable = false, length = 40)
    private MuscleGroup muscleGroup;

    public UUID getSecondaryMuscleId() { return secondaryMuscleId; }
    public void setSecondaryMuscleId(UUID secondaryMuscleId) { this.secondaryMuscleId = secondaryMuscleId; }
    public ExerciseEntity getExercise() { return exercise; }
    public void setExercise(ExerciseEntity exercise) { this.exercise = exercise; }
    public MuscleGroup getMuscleGroup() { return muscleGroup; }
    public void setMuscleGroup(MuscleGroup muscleGroup) { this.muscleGroup = muscleGroup; }
}
