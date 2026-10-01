package com.fitsync.nutrition.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Vitamins and minerals over a range. The averages divide by the days that have a logged meal, not by the days
 * in the range. referenceRange is FEMALE, MALE or BLENDED, or null when nothing is known about the person. focusCodes lists the nutrients a vegetarian or vegan diet most often misses, and they come first.
 */
public record NutrientSummaryView(
        LocalDate from,
        LocalDate to,
        int days,
        int daysLogged,
        String referenceLabel,
        String referenceRange,
        List<String> focusCodes,
        NutrientCoverageView coverage,
        List<NutrientSummaryItemView> nutrients) {

    public NutrientSummaryView {
        focusCodes = focusCodes == null ? List.of() : List.copyOf(focusCodes);
        nutrients = nutrients == null ? List.of() : List.copyOf(nutrients);
    }

    @Override
    public List<String> focusCodes() {
        return List.copyOf(focusCodes);
    }

    @Override
    public List<NutrientSummaryItemView> nutrients() {
        return List.copyOf(nutrients);
    }
}
