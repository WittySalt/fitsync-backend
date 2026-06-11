package com.fitsync.user.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BodyMetricEntry(
        UUID metricId,
        UUID userId,
        BigDecimal weightKg,
        BigDecimal bodyFatRate,
        BigDecimal waistCm,
        BigDecimal hipCm,
        BigDecimal chestCm,
        BigDecimal armCm,
        BigDecimal thighCm,
        BigDecimal calfCm,
        Instant measuredAt,
        MetricSource source,
        String note,
        Instant createdAt) {
}
