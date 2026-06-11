package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record MealItemView(
        UUID mealItemId,
        UUID customFoodId,
        UUID foodCatalogItemId,
        UUID foodServingId,
        String externalFoodRef,
        String barcode,
        String foodName,
        String brandName,
        BigDecimal quantity,
        String unit,
        BigDecimal servingSize,
        String servingUnit,
        String snapshotServingLabel,
        Integer calories,
        BigDecimal proteinGrams,
        BigDecimal carbsGrams,
        BigDecimal fatGrams,
        BigDecimal fiberGrams,
        BigDecimal sugarGrams,
        BigDecimal sodiumMg,
        List<NutrientDetailView> nutrientDetails) {

    public MealItemView {
        nutrientDetails = nutrientDetails == null ? List.of() : List.copyOf(nutrientDetails);
    }

    @Override
    public List<NutrientDetailView> nutrientDetails() {
        return List.copyOf(nutrientDetails);
    }
}
