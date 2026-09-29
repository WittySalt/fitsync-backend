package com.fitsync.user.dto;

import java.time.Instant;

public record OutboxPendingResponse(
        long pending,
        Instant oldestCreatedAt
) {
}
