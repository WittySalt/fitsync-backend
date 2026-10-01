package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Protein spread over the day's meals against what one meal is worth. The range per meal is 0.3 to 0.4 grams
 * per kilogram of body weight, or 20 to 40 grams when the weight is not known, in which case weightKg is null.
 */
public record ProteinByMealView(
        BigDecimal weightKg,
        int lowGrams,
        int highGrams,
        List<ProteinMealView> meals) {

    public ProteinByMealView {
        meals = meals == null ? List.of() : List.copyOf(meals);
    }

    @Override
    public List<ProteinMealView> meals() {
        return List.copyOf(meals);
    }
}
