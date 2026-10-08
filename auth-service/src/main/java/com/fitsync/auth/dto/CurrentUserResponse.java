package com.fitsync.auth.dto;

import java.util.UUID;

public record CurrentUserResponse(
        UUID userId,
        String email,
        String displayName,
        String role,
        String authProvider) {
}
