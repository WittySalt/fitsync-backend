package com.fitsync.analytics;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.analytics.messaging.UserPreferencesUpdatedKafkaListener;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserPreferencesUpdatedPayload;
import org.junit.jupiter.api.Test;

class UserPreferencesUpdatedKafkaListenerTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @SuppressWarnings("unchecked")
    void consumesUserPreferencesUpdatedEvent() throws Exception {
        DomainEventConsumer<UserPreferencesUpdatedPayload> consumer = mock(DomainEventConsumer.class);
        UserPreferencesUpdatedKafkaListener listener = new UserPreferencesUpdatedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<UserPreferencesUpdatedPayload> event = event("UserPreferencesUpdated");

        listener.onMessage(objectMapper.writeValueAsString(event), "user-events", event.userId().toString());

        verify(consumer).consume(event);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ignoresOtherEventsOnSharedTopic() throws Exception {
        DomainEventConsumer<UserPreferencesUpdatedPayload> consumer = mock(DomainEventConsumer.class);
        UserPreferencesUpdatedKafkaListener listener = new UserPreferencesUpdatedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<UserPreferencesUpdatedPayload> event = event("GoalUpdated");

        listener.onMessage(objectMapper.writeValueAsString(event), "user-events", event.userId().toString());

        verifyNoInteractions(consumer);
    }

    private FitSyncDomainEvent<UserPreferencesUpdatedPayload> event(String eventType) {
        Instant at = Instant.parse("2026-06-25T04:00:00Z");
        return new FitSyncDomainEvent<>(
                UUID.randomUUID(), eventType, 1, at, "user-service", UUID.randomUUID(),
                new UserPreferencesUpdatedPayload("100001", 4, "300001", true,
                        List.of(), List.of(), "Asia/Singapore"));
    }
}
