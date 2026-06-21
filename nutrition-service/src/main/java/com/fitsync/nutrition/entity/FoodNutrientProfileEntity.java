package com.fitsync.nutrition.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseEffectiveEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "food_nutrient_profiles")
public class FoodNutrientProfileEntity extends BaseEffectiveEntity implements NutritionFactsCarrier {

    @Id
    @Column(name = "nutrient_profile_id", nullable = false)
    private UUID nutrientProfileId;

    @Column(name = "food_id", nullable = false)
    private UUID foodId;

    @Column(name = "basis_quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal basisQuantity;

    @Column(name = "basis_unit", nullable = false, length = 30)
    private String basisUnit;

    @Embedded
    private NutritionFacts nutritionFacts = new NutritionFacts();

    @Column(name = "saturated_fat_grams", precision = 10, scale = 2)
    private BigDecimal saturatedFatGrams;

    @Column(name = "trans_fat_grams", precision = 10, scale = 2)
    private BigDecimal transFatGrams;

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

    @Override
    public NutritionFacts nutritionFactsSnapshot() {
        return NutritionFacts.copyOf(nutritionFacts);
    }

    @Override
    public void replaceNutritionFacts(NutritionFacts nutritionFacts) {
        this.nutritionFacts = NutritionFacts.copyOf(nutritionFacts);
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
