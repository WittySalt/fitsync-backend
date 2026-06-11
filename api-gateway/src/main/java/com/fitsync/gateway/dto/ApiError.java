package com.fitsync.gateway.dto;

import java.time.Instant;

public record ApiError(
        String code,
        String message,
        String path,
        Instant timestamp,
        String correlationId) {
}
