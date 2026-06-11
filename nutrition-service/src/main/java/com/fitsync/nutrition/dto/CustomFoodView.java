package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomFoodView(
        UUID customFoodId,
        String name,
        String brandName,
        BigDecimal servingSize,
        String servingUnit,
        Integer calories,
        BigDecimal proteinGrams,
        BigDecimal carbsGrams,
        BigDecimal fatGrams,
        BigDecimal fiberGrams,
        BigDecimal sugarGrams,
        BigDecimal sodiumMg,
        boolean verified) {
}
