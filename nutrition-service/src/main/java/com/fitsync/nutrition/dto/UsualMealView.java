package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.domain.MealType;

/**
 * What the caller usually eats for one kind of meal. When no combination of foods repeats often
 * enough, {@code occurrences} is 0, the source meal and the totals are null and {@code items} is empty.
 */
public record UsualMealView(
        MealType mealType,
        String label,
        String usualTime,
        int mealsInWindow,
        int occurrences,
        UUID sourceMealId,
        Integer totalCalories,
        BigDecimal totalProteinGrams,
        BigDecimal totalCarbsGrams,
        BigDecimal totalFatGrams,
        List<UsualMealItemView> items) {

    public UsualMealView {
        items = items == null ? List.of() : List.copyOf(items);
    }

    @Override
    public List<UsualMealItemView> items() {
        return List.copyOf(items);
    }
}
