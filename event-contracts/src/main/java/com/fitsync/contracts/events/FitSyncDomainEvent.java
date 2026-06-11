package com.fitsync.contracts.events;

import java.time.Instant;
import java.util.UUID;

public record FitSyncDomainEvent<T>(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String source,
        UUID userId,
        T payload) {
}
