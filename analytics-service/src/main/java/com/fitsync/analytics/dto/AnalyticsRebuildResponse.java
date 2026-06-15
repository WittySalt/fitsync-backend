package com.fitsync.analytics.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AnalyticsRebuildResponse(
        UUID userId,
        String scope,
        LocalDate date,
        Instant rebuiltAt) {
}
