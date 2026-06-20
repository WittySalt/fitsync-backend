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
@Table(name = "exercise_instructions")
public class ExerciseInstructionEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "instruction_id", nullable = false)
    private UUID instructionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private ExerciseEntity exercise;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(name = "instruction_text", nullable = false, length = 1000)
    private String text;

    public UUID getInstructionId() { return instructionId; }
    public void setInstructionId(UUID instructionId) { this.instructionId = instructionId; }
    public ExerciseEntity getExercise() { return exercise; }
    public void setExercise(ExerciseEntity exercise) { this.exercise = exercise; }
    public Integer getStepNumber() { return stepNumber; }
    public void setStepNumber(Integer stepNumber) { this.stepNumber = stepNumber; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
