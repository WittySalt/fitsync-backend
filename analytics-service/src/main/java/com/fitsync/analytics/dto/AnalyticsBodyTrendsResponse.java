package com.fitsync.analytics.dto;

import java.time.LocalDate;
import java.util.List;

public record AnalyticsBodyTrendsResponse(
        LocalDate from,
        LocalDate to,
        String bucket,
        List<BodyTrendPoint> points) {

    public record BodyTrendPoint(
            LocalDate date,
            Double weightKg,
            Double bodyFatRate,
            Double waistCm,
            Double bmi,
            Double weightDeltaFromStart,
            Double weightDeltaFromPrevious) {
    }
}
