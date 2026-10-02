package com.fitsync.nutrition.dto;

import java.math.BigDecimal;

/** One food the caller ate in the range, with what it gave of a nutrient per logged day and its part of the total. */
public record NutrientSourceView(
        String name,
        String foodGroup,
        String categoryCode,
        BigDecimal amount,
        BigDecimal share) {
}
