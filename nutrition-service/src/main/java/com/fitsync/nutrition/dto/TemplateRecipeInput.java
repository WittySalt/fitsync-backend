package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** A recipe placed in a meal template, with the number of servings of it. */
public record TemplateRecipeInput(
        @NotNull UUID recipeId,
        @NotNull @DecimalMin("0.25") @DecimalMax("20") BigDecimal servings) {
}
