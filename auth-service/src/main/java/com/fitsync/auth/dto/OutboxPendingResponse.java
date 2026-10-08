package com.fitsync.auth.dto;

import java.time.Instant;

public record OutboxPendingResponse(
        long pending,
        Instant oldestCreatedAt
) {
}
