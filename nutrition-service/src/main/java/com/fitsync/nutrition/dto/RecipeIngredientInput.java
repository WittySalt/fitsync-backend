package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * One recipe ingredient: a catalog food + quantity. {@code foodServingId} is optional — the food's
 * default serving is used when omitted. (v1 supports catalog foods only; custom-food ingredients
 * are a follow-up.)
 */
public record RecipeIngredientInput(
        @NotNull UUID foodCatalogItemId,
        UUID foodServingId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity) {
}
