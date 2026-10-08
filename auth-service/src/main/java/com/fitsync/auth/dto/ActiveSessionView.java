package com.fitsync.auth.dto;

import java.time.Instant;

public record ActiveSessionView(
        String sessionId,
        String deviceLabel,
        String ipAddress,
        Instant issuedAt,
        Instant expiresAt,
        boolean current,
        boolean revocable) {
}
