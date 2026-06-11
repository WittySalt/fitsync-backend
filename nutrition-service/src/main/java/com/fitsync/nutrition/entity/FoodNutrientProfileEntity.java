package com.fitsync.nutrition.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseEffectiveEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "food_nutrient_profiles")
public class FoodNutrientProfileEntity extends BaseEffectiveEntity {

    @Id
    @Column(name = "nutrient_profile_id", nullable = false)
    private UUID nutrientProfileId;

    @Column(name = "food_id", nullable = false)
    private UUID foodId;

    @Column(name = "basis_quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal basisQuantity;

    @Column(name = "basis_unit", nullable = false, length = 30)
    private String basisUnit;

    @Column(name = "calories", nullable = false)
    private Integer calories;

    @Column(name = "protein_grams", precision = 10, scale = 2)
    private BigDecimal proteinGrams;

    @Column(name = "carbs_grams", precision = 10, scale = 2)
    private BigDecimal carbsGrams;

    @Column(name = "fat_grams", precision = 10, scale = 2)
    private BigDecimal fatGrams;

    @Column(name = "saturated_fat_grams", precision = 10, scale = 2)
    private BigDecimal saturatedFatGrams;

    @Column(name = "trans_fat_grams", precision = 10, scale = 2)
    private BigDecimal transFatGrams;

    @Column(name = "fiber_grams", precision = 10, scale = 2)
    private BigDecimal fiberGrams;

    @Column(name = "sugar_grams", precision = 10, scale = 2)
    private BigDecimal sugarGrams;

    @Column(name = "sodium_mg", precision = 10, scale = 2)
    private BigDecimal sodiumMg;

    @Column(name = "potassium_mg", precision = 10, scale = 2)
    private BigDecimal potassiumMg;

    @Column(name = "cholesterol_mg", precision = 10, scale = 2)
    private BigDecimal cholesterolMg;

    public UUID getNutrientProfileId() {
        return nutrientProfileId;
    }

    public void setNutrientProfileId(UUID nutrientProfileId) {
        this.nutrientProfileId = nutrientProfileId;
    }

    public UUID getFoodId() {
        return foodId;
    }

    public void setFoodId(UUID foodId) {
        this.foodId = foodId;
    }

    public BigDecimal getBasisQuantity() {
        return basisQuantity;
    }

    public void setBasisQuantity(BigDecimal basisQuantity) {
        this.basisQuantity = basisQuantity;
    }

    public String getBasisUnit() {
        return basisUnit;
    }

    public void setBasisUnit(String basisUnit) {
        this.basisUnit = basisUnit;
    }

    public Integer getCalories() {
        return calories;
    }

    public void setCalories(Integer calories) {
        this.calories = calories;
    }

    public BigDecimal getProteinGrams() {
        return proteinGrams;
    }

    public void setProteinGrams(BigDecimal proteinGrams) {
        this.proteinGrams = proteinGrams;
    }

    public BigDecimal getCarbsGrams() {
        return carbsGrams;
    }

    public void setCarbsGrams(BigDecimal carbsGrams) {
        this.carbsGrams = carbsGrams;
    }

    public BigDecimal getFatGrams() {
        return fatGrams;
    }

    public void setFatGrams(BigDecimal fatGrams) {
        this.fatGrams = fatGrams;
    }

    public BigDecimal getSaturatedFatGrams() {
        return saturatedFatGrams;
    }

    public void setSaturatedFatGrams(BigDecimal saturatedFatGrams) {
        this.saturatedFatGrams = saturatedFatGrams;
    }

    public BigDecimal getTransFatGrams() {
        return transFatGrams;
    }

    public void setTransFatGrams(BigDecimal transFatGrams) {
        this.transFatGrams = transFatGrams;
    }

    public BigDecimal getFiberGrams() {
        return fiberGrams;
    }

    public void setFiberGrams(BigDecimal fiberGrams) {
        this.fiberGrams = fiberGrams;
    }

    public BigDecimal getSugarGrams() {
        return sugarGrams;
    }

    public void setSugarGrams(BigDecimal sugarGrams) {
        this.sugarGrams = sugarGrams;
    }

    public BigDecimal getSodiumMg() {
        return sodiumMg;
    }

    public void setSodiumMg(BigDecimal sodiumMg) {
        this.sodiumMg = sodiumMg;
    }

    public BigDecimal getPotassiumMg() {
        return potassiumMg;
    }

    public void setPotassiumMg(BigDecimal potassiumMg) {
        this.potassiumMg = potassiumMg;
    }

    public BigDecimal getCholesterolMg() {
        return cholesterolMg;
    }

    public void setCholesterolMg(BigDecimal cholesterolMg) {
        this.cholesterolMg = cholesterolMg;
    }
}
