package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * An ingredient as written, with the catalogue food it was matched to and the weight used for the nutrition.
 * {@code name} is the short name to show: the ingredient word of the written line for imported recipes, the
 * food's display name for the user's own.
 */
public record RecipeDetailIngredientView(
        UUID ingredientId,
        String displayText,
        UUID foodCatalogItemId,
        String foodName,
        String name,
        BigDecimal grams,
        String foodGroup,
        String categoryCode) {
}
