package com.fitsync.contracts.events.user;

import java.time.Instant;

public record UserRegisteredPayload(
        String email,
        String displayName,
        String role,
        Instant registeredAt) {
}
