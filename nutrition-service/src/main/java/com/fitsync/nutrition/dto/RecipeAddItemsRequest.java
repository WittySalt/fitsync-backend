package com.fitsync.nutrition.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Add servings of a recipe to a meal that is already logged. */
public record RecipeAddItemsRequest(
        @NotNull @DecimalMin("0.25") @DecimalMax("20") BigDecimal servings) {
}
