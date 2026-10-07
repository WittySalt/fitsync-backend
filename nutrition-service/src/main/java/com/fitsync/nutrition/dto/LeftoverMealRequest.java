package com.fitsync.nutrition.dto;

import java.time.Instant;

import com.fitsync.nutrition.domain.MealType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Logs servings of a cook's leftovers as a meal. servings defaults to 1, consumedAt to now. */
public record LeftoverMealRequest(
        @NotNull MealType mealType,
        @Min(1) @Max(20) Integer servings,
        Instant consumedAt) {
}
