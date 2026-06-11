package com.fitsync.user.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fitsync.user.domain.MetricSource;

public record BodyMetricView(
        UUID metricId,
        BigDecimal weightKg,
        BigDecimal bodyFatRate,
        BigDecimal waistCm,
        BigDecimal hipCm,
        BigDecimal chestCm,
        BigDecimal armCm,
        BigDecimal thighCm,
        BigDecimal calfCm,
        BigDecimal bmi,
        Instant measuredAt,
        MetricSource source,
        String note) {
}
