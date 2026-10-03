package com.fitsync.nutrition.dto;

import java.time.Instant;

import com.fitsync.nutrition.domain.MealType;
import jakarta.validation.constraints.NotNull;

/** Turn a template into a real meal at {@code consumedAt}; {@code mealType} optionally overrides it. */
public record MaterializeTemplateRequest(
        @NotNull Instant consumedAt,
        MealType mealType) {
}
