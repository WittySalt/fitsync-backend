package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record MealTemplateItemView(
        UUID templateItemId,
        UUID customFoodId,
        UUID foodCatalogItemId,
        UUID foodServingId,
        String foodName,
        String brandName,
        String brandLogoUrl,
        BigDecimal quantity,
        String unit,
        String snapshotServingLabel,
        Integer calories,
        BigDecimal proteinGrams,
        BigDecimal carbsGrams,
        BigDecimal fatGrams,
        BigDecimal fiberGrams,
        BigDecimal sugarGrams,
        BigDecimal sodiumMg,
        UUID recipeId,
        BigDecimal recipeServings) {
}
