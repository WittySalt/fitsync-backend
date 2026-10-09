package com.fitsync.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record RefreshResponse(
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        UUID sessionId) {

    @Override
    public String toString() {
        return "RefreshResponse[sessionId=" + sessionId + ", accessTokenExpiresAt=" + accessTokenExpiresAt + "]";
    }
}
