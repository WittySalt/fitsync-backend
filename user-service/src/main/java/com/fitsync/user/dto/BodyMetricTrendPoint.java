package com.fitsync.user.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record BodyMetricTrendPoint(
        BigDecimal value,
        Instant measuredAt) {
}
