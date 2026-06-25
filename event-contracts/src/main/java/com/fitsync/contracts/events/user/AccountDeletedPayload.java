package com.fitsync.contracts.events.user;

import java.time.Instant;

public record AccountDeletedPayload(
        Instant deletedAt,
        String reason) {
}
