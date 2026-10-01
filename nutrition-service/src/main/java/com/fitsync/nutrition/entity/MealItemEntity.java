package com.fitsync.nutrition.entity;

import java.math.BigDecimal;
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

    @Column(name = "saturated_fat_grams", precision = 10, scale = 2)
    private BigDecimal saturatedFatGrams;

    @Column(name = "water_ml")
    private Integer waterMl;

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

    public BigDecimal getSaturatedFatGrams() {
        return saturatedFatGrams;
    }

    public void setSaturatedFatGrams(BigDecimal saturatedFatGrams) {
        this.saturatedFatGrams = saturatedFatGrams;
    }

    public Integer getWaterMl() {
        return waterMl;
    }

    public void setWaterMl(Integer waterMl) {
        this.waterMl = waterMl;
    }
}
