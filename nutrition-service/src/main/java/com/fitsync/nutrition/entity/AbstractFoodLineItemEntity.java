package com.fitsync.nutrition.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class AbstractFoodLineItemEntity extends BaseAuditableEntity implements NutritionFactsCarrier {

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

    // --- Food-classification snapshot (additive; nullable). Captured at line-item creation so analysis
    // (e.g. food-group balance, ultra-processed share) is immune to later catalog recategorization and
    // works for custom/external foods. Meal items get it at log time in NutritionServiceImpl (SNAP-3). See
    // recommendation-service/docs/analytics-collaboration.md and docs/nutrition-data-plan.md. ---
    @Column(name = "category_code", length = 40)
    private String categoryCode;

    @Column(name = "nova_group")
    private Integer novaGroup;

    @Column(name = "nutri_score", length = 1)
    private String nutriScore;

    @Column(name = "grams_consumed", precision = 10, scale = 2)
    private BigDecimal gramsConsumed;

    @Column(name = "source_dataset", length = 30)
    private String sourceDataset;

    // The recipe a line item came from, and the servings of it, so the rows of one recipe can be shown together.
    @Column(name = "recipe_id")
    private UUID recipeId;

    @Column(name = "recipe_servings", precision = 10, scale = 2)
    private BigDecimal recipeServings;

    @Embedded
    private NutritionFacts nutritionFacts = new NutritionFacts();

    public UUID getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(UUID recipeId) {
        this.recipeId = recipeId;
    }

    public BigDecimal getRecipeServings() {
        return recipeServings;
    }

    public void setRecipeServings(BigDecimal recipeServings) {
        this.recipeServings = recipeServings;
    }

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

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public Integer getNovaGroup() {
        return novaGroup;
    }

    public void setNovaGroup(Integer novaGroup) {
        this.novaGroup = novaGroup;
    }

    public String getNutriScore() {
        return nutriScore;
    }

    public void setNutriScore(String nutriScore) {
        this.nutriScore = nutriScore;
    }

    public BigDecimal getGramsConsumed() {
        return gramsConsumed;
    }

    public void setGramsConsumed(BigDecimal gramsConsumed) {
        this.gramsConsumed = gramsConsumed;
    }

    public String getSourceDataset() {
        return sourceDataset;
    }

    public void setSourceDataset(String sourceDataset) {
        this.sourceDataset = sourceDataset;
    }

    @Override
    public NutritionFacts nutritionFactsSnapshot() {
        return NutritionFacts.copyOf(nutritionFacts);
    }

    @Override
    public void replaceNutritionFacts(NutritionFacts nutritionFacts) {
        this.nutritionFacts = NutritionFacts.copyOf(nutritionFacts);
    }
}
