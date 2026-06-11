package com.fitsync.nutrition.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseEffectiveEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "food_servings")
public class FoodServingEntity extends BaseEffectiveEntity {

    @Id
    @Column(name = "serving_id", nullable = false)
    private UUID servingId;

    @Column(name = "food_id", nullable = false)
    private UUID foodId;

    @Column(name = "label", nullable = false, length = 80)
    private String label;

    @Column(name = "quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit", nullable = false, length = 30)
    private String unit;

    @Column(name = "grams_equivalent", precision = 10, scale = 2)
    private BigDecimal gramsEquivalent;

    @Column(name = "milliliters_equivalent", precision = 10, scale = 2)
    private BigDecimal millilitersEquivalent;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "default_serving", nullable = false)
    private boolean defaultServing;

    public UUID getServingId() {
        return servingId;
    }

    public void setServingId(UUID servingId) {
        this.servingId = servingId;
    }

    public UUID getFoodId() {
        return foodId;
    }

    public void setFoodId(UUID foodId) {
        this.foodId = foodId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
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

    public BigDecimal getGramsEquivalent() {
        return gramsEquivalent;
    }

    public void setGramsEquivalent(BigDecimal gramsEquivalent) {
        this.gramsEquivalent = gramsEquivalent;
    }

    public BigDecimal getMillilitersEquivalent() {
        return millilitersEquivalent;
    }

    public void setMillilitersEquivalent(BigDecimal millilitersEquivalent) {
        this.millilitersEquivalent = millilitersEquivalent;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isDefaultServing() {
        return defaultServing;
    }

    public void setDefaultServing(boolean defaultServing) {
        this.defaultServing = defaultServing;
    }
}
