package com.fitsync.nutrition.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "meal_items")
public class MealItemEntity extends AbstractFoodLineItemEntity {

    @Id
    @Column(name = "meal_item_id", nullable = false)
    private UUID mealItemId;

    @Column(name = "meal_id", nullable = false)
    private UUID mealId;

    public UUID getMealItemId() {
        return mealItemId;
    }

    public void setMealItemId(UUID mealItemId) {
        this.mealItemId = mealItemId;
    }

    public UUID getMealId() {
        return mealId;
    }

    public void setMealId(UUID mealId) {
        this.mealId = mealId;
    }
}
