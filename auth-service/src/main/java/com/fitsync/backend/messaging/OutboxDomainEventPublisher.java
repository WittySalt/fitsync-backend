package com.fitsync.backend.messaging;

import java.time.Instant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fitsync.backend.messaging.entity.OutboxEventEntity;
import com.fitsync.backend.messaging.repository.OutboxEventRepository;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import org.springframework.stereotype.Component;

/**
 * Records a domain event in the outbox table instead of sending it, so it commits or rolls back with the
 * change that raised it; {@link OutboxEventRelay} sends it to Kafka later.
 */
@Component
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final ObjectWriter domainEventWriter;
    private final OutboxEventRepository outboxEventRepository;

    public OutboxDomainEventPublisher(ObjectMapper objectMapper,
                                      OutboxEventRepository outboxEventRepository) {
        this.domainEventWriter = objectMapper.writer();
        this.outboxEventRepository = outboxEventRepository;
    }

    @Override
    public void publish(String topic, String aggregateType, String aggregateId, FitSyncDomainEvent<?> event) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.setEventId(event.eventId());
        entity.setTopic(topic);
        entity.setEventType(event.eventType());
        entity.setAggregateType(aggregateType);
        entity.setAggregateId(aggregateId);
        entity.setUserId(event.userId());
        entity.setVersion(event.version());
        entity.setPayload(serialize(event));
        entity.setStatus(OutboxEventStatus.PENDING);
        entity.setOccurredAt(event.occurredAt());
        entity.setCreatedAt(Instant.now());
        outboxEventRepository.save(entity);
    }

    private String serialize(FitSyncDomainEvent<?> event) {
        try {
            return domainEventWriter.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize domain event " + event.eventType(), ex);
        }
    }
}
