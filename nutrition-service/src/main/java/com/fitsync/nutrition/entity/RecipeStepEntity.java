package com.fitsync.nutrition.entity;

import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One numbered step of a recipe's method. */
@Entity
@Table(name = "recipe_steps")
public class RecipeStepEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "step_id", nullable = false)
    private UUID stepId;

    @Column(name = "recipe_id", nullable = false)
    private UUID recipeId;

    @Column(name = "step_number", nullable = false)
    private int stepNumber;

    @Column(name = "text", nullable = false, length = 1000)
    private String text;

    public UUID getStepId() {
        return stepId;
    }

    public void setStepId(UUID stepId) {
        this.stepId = stepId;
    }

    public UUID getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(UUID recipeId) {
        this.recipeId = recipeId;
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public void setStepNumber(int stepNumber) {
        this.stepNumber = stepNumber;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
