package com.fitsync.nutrition.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "recipe_ingredients")
public class RecipeIngredientEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "ingredient_id", nullable = false)
    private UUID ingredientId;

    @Column(name = "recipe_id", nullable = false)
    private UUID recipeId;

    @Column(name = "food_catalog_item_id", nullable = false)
    private UUID foodCatalogItemId;

    @Column(name = "food_serving_id")
    private UUID foodServingId;

    @Column(name = "quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "display_text", length = 200)
    private String displayText;

    @Column(name = "grams", precision = 10, scale = 2)
    private BigDecimal grams;

    public UUID getIngredientId() {
        return ingredientId;
    }

    public void setIngredientId(UUID ingredientId) {
        this.ingredientId = ingredientId;
    }

    public UUID getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(UUID recipeId) {
        this.recipeId = recipeId;
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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getDisplayText() {
        return displayText;
    }

    public void setDisplayText(String displayText) {
        this.displayText = displayText;
    }

    public BigDecimal getGrams() {
        return grams;
    }

    public void setGrams(BigDecimal grams) {
        this.grams = grams;
    }
}
