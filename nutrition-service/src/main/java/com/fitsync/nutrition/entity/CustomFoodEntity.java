package com.fitsync.nutrition.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "custom_foods")
public class CustomFoodEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "custom_food_id", nullable = false)
    private UUID customFoodId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "brand_name", length = 120)
    private String brandName;

    @Column(name = "serving_size", nullable = false, precision = 10, scale = 2)
    private BigDecimal servingSize;

    @Column(name = "serving_unit", nullable = false, length = 30)
    private String servingUnit;

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

    @Column(name = "verified", nullable = false)
    private boolean verified;

    public UUID getCustomFoodId() {
        return customFoodId;
    }

    public void setCustomFoodId(UUID customFoodId) {
        this.customFoodId = customFoodId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    public BigDecimal getServingSize() {
        return servingSize;
    }

    public void setServingSize(BigDecimal servingSize) {
        this.servingSize = servingSize;
    }

    public String getServingUnit() {
        return servingUnit;
    }

    public void setServingUnit(String servingUnit) {
        this.servingUnit = servingUnit;
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

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }
}
