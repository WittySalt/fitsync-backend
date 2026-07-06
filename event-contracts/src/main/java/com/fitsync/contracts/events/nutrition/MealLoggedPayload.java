package com.fitsync.contracts.events.nutrition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MealLoggedPayload(
        UUID mealId,
        String mealType,
        Instant consumedAt,
        Integer totalCalories,
        BigDecimal totalProteinGrams,
        BigDecimal totalCarbsGrams,
        BigDecimal totalFatGrams,
        Integer itemCount,
        String source,
        Map<String, Integer> foodGroupCalories) {

    /**
     * Backward-compatible constructor for the original payload (before the food-group breakdown was
     * added). Existing producers/tests that don't supply a breakdown get an empty map.
     */
    public MealLoggedPayload(UUID mealId,
                             String mealType,
                             Instant consumedAt,
                             Integer totalCalories,
                             BigDecimal totalProteinGrams,
                             BigDecimal totalCarbsGrams,
                             BigDecimal totalFatGrams,
                             Integer itemCount,
                             String source) {
        this(mealId, mealType, consumedAt, totalCalories, totalProteinGrams, totalCarbsGrams,
                totalFatGrams, itemCount, source, Map.of());
    }

    public MealLoggedPayload {
        foodGroupCalories = foodGroupCalories == null ? Map.of() : Map.copyOf(foodGroupCalories);
    }

    /**
     * Per-food-group energy for this meal: internal {@code food_categories} code → total kcal, summed
     * over the meal's items that carry a category snapshot. Lets analytics compute food-group balance
     * ("meat-heavy / veg-light") without reading nutrition internals. Empty when no item is classified.
     */
    @Override
    public Map<String, Integer> foodGroupCalories() {
        return Map.copyOf(foodGroupCalories);
    }
}
