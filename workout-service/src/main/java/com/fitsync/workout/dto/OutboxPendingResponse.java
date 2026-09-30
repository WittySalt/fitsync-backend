package com.fitsync.workout.dto;

import java.time.Instant;

public record OutboxPendingResponse(
        long pending,
        Instant oldestCreatedAt) {
}
