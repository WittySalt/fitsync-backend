package com.fitsync.workout.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "exercises")
public class ExerciseEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "exercise_type", nullable = false, length = 30)
    private ExerciseType exerciseType;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_muscle_group", nullable = false, length = 40)
    private MuscleGroup primaryMuscleGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "equipment", nullable = false, length = 40)
    private ExerciseEquipment equipment;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false, length = 30)
    private WorkoutDifficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ExerciseStatus status;

    @Column(name = "custom", nullable = false)
    private boolean custom;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(name = "animation_url", length = 500)
    private String animationUrl;

    @Column(name = "external_source", length = 120)
    private String externalSource;

    @Column(name = "external_ref", length = 200)
    private String externalRef;

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepNumber asc")
    @BatchSize(size = 100)
    private List<ExerciseInstructionEntity> instructions = new ArrayList<>();

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position asc")
    @BatchSize(size = 100)
    private List<ExerciseImageEntity> images = new ArrayList<>();

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("muscleGroup asc")
    @BatchSize(size = 100)
    private List<ExerciseSecondaryMuscleEntity> secondaryMuscles = new ArrayList<>();

    public UUID getExerciseId() { return exerciseId; }
    public void setExerciseId(UUID exerciseId) { this.exerciseId = exerciseId; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(UUID ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ExerciseType getExerciseType() { return exerciseType; }
    public void setExerciseType(ExerciseType exerciseType) { this.exerciseType = exerciseType; }
    public MuscleGroup getPrimaryMuscleGroup() { return primaryMuscleGroup; }
    public void setPrimaryMuscleGroup(MuscleGroup primaryMuscleGroup) { this.primaryMuscleGroup = primaryMuscleGroup; }
    public ExerciseEquipment getEquipment() { return equipment; }
    public void setEquipment(ExerciseEquipment equipment) { this.equipment = equipment; }
    public WorkoutDifficulty getDifficulty() { return difficulty; }
    public void setDifficulty(WorkoutDifficulty difficulty) { this.difficulty = difficulty; }
    public ExerciseStatus getStatus() { return status; }
    public void setStatus(ExerciseStatus status) { this.status = status; }
    public boolean isCustom() { return custom; }
    public void setCustom(boolean custom) { this.custom = custom; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public String getAnimationUrl() { return animationUrl; }
    public void setAnimationUrl(String animationUrl) { this.animationUrl = animationUrl; }
    public String getExternalSource() { return externalSource; }
    public void setExternalSource(String externalSource) { this.externalSource = externalSource; }
    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }
    public List<ExerciseInstructionEntity> getInstructions() { return instructions; }
    public void setInstructions(List<ExerciseInstructionEntity> instructions) { this.instructions = instructions; }
    public List<ExerciseImageEntity> getImages() { return images; }
    public void setImages(List<ExerciseImageEntity> images) { this.images = images; }
    public List<ExerciseSecondaryMuscleEntity> getSecondaryMuscles() { return secondaryMuscles; }
    public void setSecondaryMuscles(List<ExerciseSecondaryMuscleEntity> secondaryMuscles) { this.secondaryMuscles = secondaryMuscles; }
}
