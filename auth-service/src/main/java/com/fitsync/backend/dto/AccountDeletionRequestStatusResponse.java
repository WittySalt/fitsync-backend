package com.fitsync.backend.dto;

import java.time.Instant;

public record AccountDeletionRequestStatusResponse(
        String status,
        Instant requestedAt,
        Instant scheduledFor,
        boolean passwordConfirmationRequired) {
}
