package com.fitsync.nutrition.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class NutritionFacts {

    @Column(name = "calories", nullable = false)
    private Integer calories;

    @Column(name = "protein_grams", precision = 10, scale = 2)
    private BigDecimal proteinGrams;

    @Column(name = "carbs_grams", precision = 10, scale = 2)
    private BigDecimal carbsGrams;

    @Column(name = "fat_grams", precision = 10, scale = 2)
    private BigDecimal fatGrams;

    @Column(name = "fiber_grams", precision = 10, scale = 2)
    private BigDecimal fiberGrams;

    @Column(name = "sugar_grams", precision = 10, scale = 2)
    private BigDecimal sugarGrams;

    @Column(name = "sodium_mg", precision = 10, scale = 2)
    private BigDecimal sodiumMg;

    public NutritionFacts() {
    }

    private NutritionFacts(NutritionFacts source) {
        this.calories = source.calories;
        this.proteinGrams = source.proteinGrams;
        this.carbsGrams = source.carbsGrams;
        this.fatGrams = source.fatGrams;
        this.fiberGrams = source.fiberGrams;
        this.sugarGrams = source.sugarGrams;
        this.sodiumMg = source.sodiumMg;
    }

    public static NutritionFacts copyOf(NutritionFacts source) {
        return source == null ? new NutritionFacts() : new NutritionFacts(source);
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
}
