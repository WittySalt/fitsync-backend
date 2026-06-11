package com.fitsync.backend.messaging;

import java.time.Instant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.backend.messaging.entity.OutboxEventEntity;
import com.fitsync.backend.messaging.repository.OutboxEventRepository;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import org.springframework.stereotype.Component;

@Component
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxEventRepository;

    public OutboxDomainEventPublisher(ObjectMapper objectMapper,
                                      OutboxEventRepository outboxEventRepository) {
        this.objectMapper = objectMapper;
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
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize domain event " + event.eventType(), ex);
        }
    }
}
