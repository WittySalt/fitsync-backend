package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One vitamin or mineral averaged over the logged days of a range. The share is the average divided by the
 * reference, and the reference, upper limit and flags are null or false when the nutrient has none.
 * When no logged item of the range carries the nutrient, the average, share, met and aboveUpperLimit are null.
 * richestUsualFood names the food, among the person's usual ones, that gives the most per usual portion.
 * days has one entry per date of the range, from first to last.
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
        String richestUsualFood,
        List<NutrientDayView> days) {

    public NutrientSummaryItemView {
        days = days == null ? List.of() : List.copyOf(days);
    }

    @Override
    public List<NutrientDayView> days() {
        return List.copyOf(days);
    }
}
