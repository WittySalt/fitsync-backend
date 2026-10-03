package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fitsync.nutrition.domain.MealType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Log servings of a recipe as one meal at {@code consumedAt}. */
public record RecipeMealRequest(
        @NotNull @DecimalMin("0.25") @DecimalMax("20") BigDecimal servings,
        @NotNull MealType mealType,
        @NotNull Instant consumedAt) {
}
