package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** An ingredient as written, with the catalogue food it was matched to and the weight used for the nutrition. */
public record RecipeDetailIngredientView(
        UUID ingredientId,
        String displayText,
        UUID foodCatalogItemId,
        String foodName,
        BigDecimal grams,
        String foodGroup,
        String categoryCode) {
}
