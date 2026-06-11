package com.fitsync.nutrition.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseEffectiveEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "food_nutrient_details")
public class FoodNutrientDetailEntity extends BaseEffectiveEntity {

    @Id
    @Column(name = "detail_id", nullable = false)
    private UUID detailId;

    @Column(name = "food_id", nullable = false)
    private UUID foodId;

    @Column(name = "nutrient_code", nullable = false, length = 40)
    private String nutrientCode;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public UUID getDetailId() {
        return detailId;
    }

    public void setDetailId(UUID detailId) {
        this.detailId = detailId;
    }

    public UUID getFoodId() {
        return foodId;
    }

    public void setFoodId(UUID foodId) {
        this.foodId = foodId;
    }

    public String getNutrientCode() {
        return nutrientCode;
    }

    public void setNutrientCode(String nutrientCode) {
        this.nutrientCode = nutrientCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
