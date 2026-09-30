package com.fitsync.nutrition.dto;

import java.time.Instant;

public record OutboxPendingResponse(
        long pending,
        Instant oldestCreatedAt) {
}
