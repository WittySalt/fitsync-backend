package com.fitsync.recommendation.handler;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.recommendation.entity.RecommendationProcessedEventEntity;
import com.fitsync.recommendation.repository.RecommendationProcessedEventRepository;
import org.springframework.stereotype.Service;

/**
 * Idempotency guard shared by all event handlers: Kafka delivery is at-least-once, so
 * every handler checks {@link #alreadyProcessed} first and calls {@link #record} last
 * within its transaction.
 */
@Service
public class ProcessedEventGuard {

    private final RecommendationProcessedEventRepository processedEventRepository;
    private final Clock clock;

    public ProcessedEventGuard(RecommendationProcessedEventRepository processedEventRepository, Clock clock) {
        this.processedEventRepository = processedEventRepository;
        this.clock = clock;
    }

    public boolean alreadyProcessed(FitSyncDomainEvent<?> event) {
        return processedEventRepository.existsById(event.eventId());
    }

    public void record(FitSyncDomainEvent<?> event) {
        RecommendationProcessedEventEntity processedEvent = new RecommendationProcessedEventEntity();
        processedEvent.setEventId(event.eventId());
        processedEvent.setEventType(event.eventType());
        processedEvent.setUserId(event.userId());
        processedEvent.setProcessedAt(event.occurredAt() == null ? Instant.now(clock) : event.occurredAt());
        processedEventRepository.save(processedEvent);
    }

    public void purgeForUser(UUID userId) {
        processedEventRepository.deleteByUserId(userId);
    }
}
