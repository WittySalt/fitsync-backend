package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.domain.MealSource;
import com.fitsync.nutrition.domain.MealType;

public record MealView(
        UUID mealId,
        MealType mealType,
        String name,
        MealSource source,
        Instant consumedAt,
        String timezone,
        String note,
        Integer totalCalories,
        BigDecimal totalProteinGrams,
        BigDecimal totalCarbsGrams,
        BigDecimal totalFatGrams,
        Integer itemCount,
        List<MealItemView> items,
        Instant createdAt,
        Instant updatedAt) {
}
