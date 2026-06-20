package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyNutritionSummaryView(
        LocalDate date,
        String timezone,
        Integer totalCalories,
        BigDecimal totalProteinGrams,
        BigDecimal totalCarbsGrams,
        BigDecimal totalFatGrams,
        Integer targetCalories,
        BigDecimal targetProteinGrams,
        BigDecimal targetCarbsGrams,
        BigDecimal targetFatGrams,
        Integer remainingCalories,
        BigDecimal remainingProteinGrams,
        BigDecimal remainingCarbsGrams,
        BigDecimal remainingFatGrams,
        Integer mealCount,
        BigDecimal totalFiberGrams,
        BigDecimal totalSugarGrams,
        BigDecimal totalSodiumMg,
        BigDecimal fiberTargetGrams,
        BigDecimal sugarLimitGrams,
        BigDecimal sodiumLimitMg,
        Boolean fiberTargetMet,
        Boolean sugarWithinLimit,
        Boolean sodiumWithinLimit) {
}
