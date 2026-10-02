package com.fitsync.nutrition.dto;

import java.math.BigDecimal;

/** One vitamin or mineral per serving, with its share of the caller's daily reference (0.25 is a quarter), or null without a reference. */
public record RecipeNutrientView(
        String code,
        String displayName,
        BigDecimal amount,
        String unit,
        BigDecimal share) {
}
