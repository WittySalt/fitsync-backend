package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A recipe with its ingredients and nutrition aggregated from them: {@code total*} is the whole
 * recipe, {@code perServing*} is {@code total / servings}. The per-serving values are directly
 * loggable into a meal (as a manual item carrying these macros).
 */
public record RecipeView(
        UUID recipeId,
        String name,
        BigDecimal servings,
        String note,
        Integer totalCalories,
        BigDecimal totalProteinGrams,
        BigDecimal totalCarbsGrams,
        BigDecimal totalFatGrams,
        BigDecimal totalFiberGrams,
        BigDecimal totalSugarGrams,
        BigDecimal totalSodiumMg,
        Integer perServingCalories,
        BigDecimal perServingProteinGrams,
        BigDecimal perServingCarbsGrams,
        BigDecimal perServingFatGrams,
        BigDecimal perServingFiberGrams,
        BigDecimal perServingSugarGrams,
        BigDecimal perServingSodiumMg,
        List<RecipeIngredientView> ingredients) {

    public RecipeView {
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
    }

    @Override
    public List<RecipeIngredientView> ingredients() {
        return List.copyOf(ingredients);
    }
}
