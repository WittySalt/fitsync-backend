package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One line of the Balance card: how much of a nutrient the day holds against its reference. For a TARGET the
 * line is met once the reference is reached, for a LIMIT it is over once the reference is passed. The share is
 * the amount divided by the reference, and null when there is no reference.
 */
public record NutrientBalanceView(
        String code,
        String displayName,
        String kind,
        BigDecimal amount,
        String unit,
        BigDecimal reference,
        BigDecimal share,
        boolean met,
        boolean over,
        List<TopSourceView> topSources) {

    public NutrientBalanceView {
        topSources = topSources == null ? List.of() : List.copyOf(topSources);
    }

    @Override
    public List<TopSourceView> topSources() {
        return List.copyOf(topSources);
    }
}
