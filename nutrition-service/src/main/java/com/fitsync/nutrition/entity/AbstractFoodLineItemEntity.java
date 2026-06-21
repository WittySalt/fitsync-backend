package com.fitsync.nutrition.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class AbstractFoodLineItemEntity extends BaseAuditableEntity {

    @Column(name = "custom_food_id")
    private UUID customFoodId;

    @Column(name = "food_catalog_item_id")
    private UUID foodCatalogItemId;

    @Column(name = "food_serving_id")
    private UUID foodServingId;

    @Column(name = "external_food_ref", length = 120)
    private String externalFoodRef;

    @Column(name = "barcode", length = 40)
    private String barcode;

    @Column(name = "food_name", nullable = false, length = 120)
    private String foodName;

    @Column(name = "brand_name", length = 120)
    private String brandName;

    @Column(name = "quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit", nullable = false, length = 30)
    private String unit;

    @Column(name = "serving_size", precision = 10, scale = 2)
    private BigDecimal servingSize;

    @Column(name = "serving_unit", length = 30)
    private String servingUnit;

    @Column(name = "snapshot_serving_label", length = 80)
    private String snapshotServingLabel;

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

    public UUID getCustomFoodId() {
        return customFoodId;
    }

    public void setCustomFoodId(UUID customFoodId) {
        this.customFoodId = customFoodId;
    }

    public UUID getFoodCatalogItemId() {
        return foodCatalogItemId;
    }

    public void setFoodCatalogItemId(UUID foodCatalogItemId) {
        this.foodCatalogItemId = foodCatalogItemId;
    }

    public UUID getFoodServingId() {
        return foodServingId;
    }

    public void setFoodServingId(UUID foodServingId) {
        this.foodServingId = foodServingId;
    }

    public String getExternalFoodRef() {
        return externalFoodRef;
    }

    public void setExternalFoodRef(String externalFoodRef) {
        this.externalFoodRef = externalFoodRef;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getFoodName() {
        return foodName;
    }

    public void setFoodName(String foodName) {
        this.foodName = foodName;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
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

    public String getSnapshotServingLabel() {
        return snapshotServingLabel;
    }

    public void setSnapshotServingLabel(String snapshotServingLabel) {
        this.snapshotServingLabel = snapshotServingLabel;
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
