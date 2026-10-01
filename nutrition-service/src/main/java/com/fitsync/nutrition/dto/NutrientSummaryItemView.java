package com.fitsync.nutrition.dto;

import java.math.BigDecimal;

/**
 * One vitamin or mineral averaged over the logged days of a range. The share is the average divided by the
 * reference, and the reference, upper limit and flags are null or false when the nutrient has none.
 * When no logged item of the range carries the nutrient, the average, share, met and aboveUpperLimit are null.
 * richestUsualFood names the food, among the person's usual ones, that gives the most per usual portion.
 */
public record NutrientSummaryItemView(
        String code,
        String displayName,
        String kind,
        String unit,
        BigDecimal averageAmount,
        BigDecimal reference,
        BigDecimal share,
        Boolean met,
        BigDecimal upperLimit,
        Boolean aboveUpperLimit,
        String richestUsualFood) {
}
