package com.fitsync.nutrition.messaging;

import java.time.Instant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.nutrition.messaging.entity.OutboxEventEntity;
import com.fitsync.nutrition.messaging.repository.OutboxEventRepository;
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
        entity.setEventVersion(event.version());
        entity.setPayload(serialize(event));
        entity.setStatus(OutboxEventStatus.PENDING);
        entity.setDateTimeProduced(event.occurredAt());
        entity.setDateTimeCreated(Instant.now());
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
