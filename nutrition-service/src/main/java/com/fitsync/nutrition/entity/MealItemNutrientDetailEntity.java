package com.fitsync.nutrition.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "meal_item_nutrient_details")
public class MealItemNutrientDetailEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "detail_id", nullable = false)
    private UUID detailId;

    @Column(name = "meal_item_id", nullable = false)
    private UUID mealItemId;

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

    public UUID getMealItemId() {
        return mealItemId;
    }

    public void setMealItemId(UUID mealItemId) {
        this.mealItemId = mealItemId;
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
