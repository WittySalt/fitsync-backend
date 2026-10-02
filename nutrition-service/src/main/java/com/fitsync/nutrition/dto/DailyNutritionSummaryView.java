package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
        Boolean sodiumWithinLimit,
        BigDecimal totalSaturatedFatGrams,
        BigDecimal totalPotassiumMg,
        Integer waterMl,
        Integer waterFromDrinksMl,
        BigDecimal sugarFromDrinksGrams,
        List<NutrientBalanceView> balance,
        ProteinByMealView proteinByMeal) {

    public DailyNutritionSummaryView {
        balance = balance == null ? List.of() : List.copyOf(balance);
    }

    @Override
    public List<NutrientBalanceView> balance() {
        return List.copyOf(balance);
    }
}
