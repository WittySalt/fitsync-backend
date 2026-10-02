package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** A catalogue food that is rich in a nutrient, with the amount in 100 g (or 100 ml for a drink). */
public record NutrientRichFoodView(
        UUID foodCatalogItemId,
        String name,
        String foodGroup,
        String categoryCode,
        BigDecimal amountPer100g) {
}
