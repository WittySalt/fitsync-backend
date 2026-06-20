package com.fitsync.workout.entity;

import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "exercise_images")
public class ExerciseImageEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "image_id", nullable = false)
    private UUID imageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private ExerciseEntity exercise;

    @Column(name = "image_position", nullable = false)
    private Integer position;

    @Column(name = "image_url", nullable = false, length = 500)
    private String url;

    public UUID getImageId() { return imageId; }
    public void setImageId(UUID imageId) { this.imageId = imageId; }
    public ExerciseEntity getExercise() { return exercise; }
    public void setExercise(ExerciseEntity exercise) { this.exercise = exercise; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}
