package com.fitsync.nutrition.dto;

import java.math.BigDecimal;

/** The daily amounts of a nutrient for one age band. mine marks the band of the caller. */
public record NutrientAmountRowView(
        String ageBand,
        String label,
        BigDecimal female,
        BigDecimal male,
        BigDecimal upperLimit,
        boolean mine) {
}
