package com.fitsync.user.dto;

import java.math.BigDecimal;

public record BodyMetricTrend(
        BodyMetricTrendPoint first,
        BodyMetricTrendPoint latest,
        BigDecimal change) {
}
