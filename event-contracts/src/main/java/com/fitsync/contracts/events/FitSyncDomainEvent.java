package com.fitsync.contracts.events;

import java.time.Instant;
import java.util.UUID;

/**
 * The envelope every domain event travels in on Kafka.
 *
 * @param eventType names the payload type; consumers on a shared topic skip the types they do not handle
 * @param version   the payload's schema version
 * @param source    the service that produced the event
 * @param userId    the user the event belongs to, or null for an event that belongs to no user
 */
public record FitSyncDomainEvent<T>(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String source,
        UUID userId,
        T payload) {
}
