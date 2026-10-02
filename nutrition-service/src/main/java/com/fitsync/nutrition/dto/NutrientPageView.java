package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the page of one nutrient shows: the caller's average over the logged days of the range against
 * the reference, each day, the foods that gave it, the amounts of every age band, the richest catalogue foods
 * and the guide. Amounts and flags are null when the range holds no value for the nutrient or the nutrient has
 * no reference.
 */
public record NutrientPageView(
        String code,
        String displayName,
        String unit,
        String groupCode,
        String symbol,
        Integer atomicNumber,
        LocalDate from,
        LocalDate to,
        int days,
        int daysLogged,
        BigDecimal averageAmount,
        BigDecimal reference,
        BigDecimal share,
        Boolean met,
        BigDecimal upperLimit,
        Boolean aboveUpperLimit,
        String referenceLabel,
        List<NutrientDayView> dayValues,
        List<NutrientSourceView> sources,
        List<NutrientAmountRowView> amounts,
        List<NutrientRichFoodView> richest,
        NutrientGuideView guide) {

    public NutrientPageView {
        dayValues = dayValues == null ? List.of() : List.copyOf(dayValues);
        sources = sources == null ? List.of() : List.copyOf(sources);
        amounts = amounts == null ? List.of() : List.copyOf(amounts);
        richest = richest == null ? List.of() : List.copyOf(richest);
    }

    @Override
    public List<NutrientDayView> dayValues() {
        return List.copyOf(dayValues);
    }

    @Override
    public List<NutrientSourceView> sources() {
        return List.copyOf(sources);
    }

    @Override
    public List<NutrientAmountRowView> amounts() {
        return List.copyOf(amounts);
    }

    @Override
    public List<NutrientRichFoodView> richest() {
        return List.copyOf(richest);
    }
}
