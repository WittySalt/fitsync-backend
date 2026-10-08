package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One recipe ingredient: a catalog food + quantity. {@code foodServingId} is optional — the food's
 * default serving is used when omitted. When {@code grams} is set the ingredient is counted by that weight
 * instead and the serving is ignored. Only catalog foods can be ingredients; custom foods cannot.
 */
public record RecipeIngredientInput(
        @NotNull UUID foodCatalogItemId,
        UUID foodServingId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity,
        @Size(max = 200) String displayText,
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal grams) {

    /** The common case of a catalogue food and a quantity of one of its servings. */
    public RecipeIngredientInput(UUID foodCatalogItemId, UUID foodServingId, BigDecimal quantity) {
        this(foodCatalogItemId, foodServingId, quantity, null, null);
    }
}
