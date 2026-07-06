package com.fitsync.nutrition.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "custom_foods")
public class CustomFoodEntity extends BaseAuditableEntity implements NutritionFactsCarrier {

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

    // Internal food_categories code for this custom food (nullable). Lets the line-item snapshot
    // classify custom foods, which otherwise have no category. See nutrition-data-plan.md §4.1.
    @Column(name = "category_code", length = 40)
    private String categoryCode;

    @Embedded
    private NutritionFacts nutritionFacts = new NutritionFacts();

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

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    @Override
    public NutritionFacts nutritionFactsSnapshot() {
        return NutritionFacts.copyOf(nutritionFacts);
    }

    @Override
    public void replaceNutritionFacts(NutritionFacts nutritionFacts) {
        this.nutritionFacts = NutritionFacts.copyOf(nutritionFacts);
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }
}
