package com.fitsync.contracts.events.nutrition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MealUpdatedPayload(
        UUID mealId,
        String mealType,
        Instant consumedAt,
        Integer totalCalories,
        BigDecimal totalProteinGrams,
        BigDecimal totalCarbsGrams,
        BigDecimal totalFatGrams,
        Integer itemCount,
        String source) {
}
