package com.fitsync.contracts.events.user;

import java.time.Instant;

/**
 * Emitted by auth-service when a pending account deletion is cancelled, because the user signed in
 * again before the removal date. The user is carried on the {@code FitSyncDomainEvent} envelope.
 */
public record AccountDeletionCancelledPayload(
        Instant cancelledAt) {
}
