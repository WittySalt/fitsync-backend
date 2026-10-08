package com.fitsync.backend.dto;

import java.util.UUID;

public record AuthResponse(
        UUID userId,
        String email,
        String displayName,
        String role,
        String tokenType,
        String accessToken) {
}
