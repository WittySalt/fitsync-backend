package com.fitsync.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Weight progress for the signed-in user, read in the timezone the caller asked for. Every figure
 * except {@code timezone} and {@code referenceDate} is null when the data behind it is missing.
 */
public record ProgressInsightResponse(
        String timezone,
        LocalDate referenceDate,
        BigDecimal weightKg,
        LocalDate measuredOn,
        int entries,
        BigDecimal weeklyRateKg,
        Integer rateWindowDays,
        BigDecimal goalWeightKg,
        LocalDate goalDate,
        LocalDate projectedDate,
        List<ProgressInsightPoint> series) {

    public ProgressInsightResponse {
        series = series == null ? List.of() : List.copyOf(series);
    }

    public List<ProgressInsightPoint> series() {
        return List.copyOf(series);
    }
}
