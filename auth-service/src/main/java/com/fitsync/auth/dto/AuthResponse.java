package com.fitsync.auth.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * What sign-in returns: the account, a short-lived access token whose {@code sid} claim is {@code sessionId}, and
 * the opaque refresh token for {@code POST /api/v1/auth/refresh}.
 */
public record AuthResponse(
        UUID userId,
        String email,
        String displayName,
        String role,
        String tokenType,
        String accessToken,
        String refreshToken,
        Instant accessTokenExpiresAt,
        UUID sessionId) {

    /** Leaves both tokens out, so a logged response never carries a credential. */
    @Override
    public String toString() {
        return "AuthResponse[userId=" + userId + ", role=" + role + ", sessionId=" + sessionId
                + ", accessTokenExpiresAt=" + accessTokenExpiresAt + "]";
    }
}
