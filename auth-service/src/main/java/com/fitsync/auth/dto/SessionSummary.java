package com.fitsync.auth.dto;

import java.time.Instant;
import java.util.UUID;

/** One of the caller's active sessions; {@code current} marks the one the request was made with. */
public record SessionSummary(
        UUID sessionId,
        String deviceLabel,
        Instant createdAt,
        Instant lastSeenAt,
        boolean current) {
}
