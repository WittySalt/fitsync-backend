package com.fitsync.nutrition.dto;

import java.time.Instant;

import com.fitsync.nutrition.domain.MealType;
import jakarta.validation.constraints.NotNull;

/** Copy a meal (with its item snapshots) to a new time. {@code mealType} optionally overrides it. */
public record CopyMealRequest(
        @NotNull Instant consumedAt,
        MealType mealType) {
}
