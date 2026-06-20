package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** An ingredient with its resolved food snapshot and computed nutrition contribution. */
public record RecipeIngredientView(
        UUID ingredientId,
        UUID foodCatalogItemId,
        String foodName,
        String brandName,
        UUID foodServingId,
        String servingLabel,
        BigDecimal quantity,
        String unit,
        Integer calories,
        BigDecimal proteinGrams,
        BigDecimal carbsGrams,
        BigDecimal fatGrams,
        BigDecimal fiberGrams,
        BigDecimal sugarGrams,
        BigDecimal sodiumMg) {
}
