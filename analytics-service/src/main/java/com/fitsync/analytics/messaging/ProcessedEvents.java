package com.fitsync.analytics.messaging;

import java.time.Clock;
import java.time.Instant;

import com.fitsync.analytics.entity.AnalyticsProcessedEventEntity;
import com.fitsync.analytics.repository.AnalyticsProcessedEventRepository;
import com.fitsync.contracts.events.FitSyncDomainEvent;

/**
 * Writes the processed-event row that makes a redelivered event a no-op. Every projection records its
 * events the same way, so they share this instead of each keeping a copy.
 */
final class ProcessedEvents {

    private ProcessedEvents() {
    }

    /**
     * Records the event as processed, stamped with when it occurred. An event without an occurrence time
     * is stamped with the injected clock rather than the system clock, so the time is the one the rest of
     * the projection used and tests can pin it.
     */
    static void record(AnalyticsProcessedEventRepository processedEventRepository,
                       FitSyncDomainEvent<?> event,
                       Clock clock) {
        AnalyticsProcessedEventEntity processedEvent = new AnalyticsProcessedEventEntity();
        processedEvent.setEventId(event.eventId());
        processedEvent.setEventType(event.eventType());
        processedEvent.setUserId(event.userId());
        processedEvent.setProcessedAt(event.occurredAt() != null ? event.occurredAt() : Instant.now(clock));
        processedEventRepository.save(processedEvent);
    }
}
