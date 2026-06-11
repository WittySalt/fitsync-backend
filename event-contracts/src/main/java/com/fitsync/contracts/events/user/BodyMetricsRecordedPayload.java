package com.fitsync.contracts.events.user;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BodyMetricsRecordedPayload(
        UUID metricId,
        BigDecimal weightKg,
        BigDecimal bodyFatRate,
        BigDecimal waistCm,
        Instant measuredAt) {
}
