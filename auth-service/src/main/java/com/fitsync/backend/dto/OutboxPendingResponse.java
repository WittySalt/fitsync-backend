package com.fitsync.backend.dto;

import java.time.Instant;

public record OutboxPendingResponse(
        long pending,
        Instant oldestCreatedAt
) {
}
