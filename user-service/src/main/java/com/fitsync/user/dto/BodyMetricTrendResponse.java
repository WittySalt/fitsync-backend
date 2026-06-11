package com.fitsync.user.dto;

import java.time.Instant;

public record BodyMetricTrendResponse(
        Instant from,
        Instant to,
        int sampleCount,
        BodyMetricTrend weightKg,
        BodyMetricTrend bodyFatRate,
        BodyMetricTrend waistCm,
        BodyMetricTrend bmi) {
}
