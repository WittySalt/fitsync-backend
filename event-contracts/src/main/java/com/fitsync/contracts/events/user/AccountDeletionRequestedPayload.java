package com.fitsync.contracts.events.user;

import java.time.Instant;

/**
 * Emitted by auth-service when it accepts an account deletion request. The account is removed at
 * {@code scheduledFor} unless the user signs in before then. The user is carried on the
 * {@code FitSyncDomainEvent} envelope.
 */
public record AccountDeletionRequestedPayload(
        Instant requestedAt,
        Instant scheduledFor) {
}
