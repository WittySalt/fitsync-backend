package com.fitsync.nutrition;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserPreferencesUpdatedPayload;
import com.fitsync.nutrition.messaging.UserPreferencesUpdatedEventConsumer;
import com.fitsync.nutrition.messaging.UserPreferencesUpdatedKafkaListener;
import org.junit.jupiter.api.Test;

class UserPreferencesUpdatedKafkaListenerTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void consumesUserPreferencesUpdatedEventFromKafkaPayload() throws Exception {
        UserPreferencesUpdatedEventConsumer consumer = mock(UserPreferencesUpdatedEventConsumer.class);
        UserPreferencesUpdatedKafkaListener listener = new UserPreferencesUpdatedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<UserPreferencesUpdatedPayload> event = new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "UserPreferencesUpdated",
                1,
                Instant.parse("2026-06-12T00:20:00Z"),
                "user-service",
                UUID.randomUUID(),
                new UserPreferencesUpdatedPayload("100001", 4, "300003", true, List.of(), List.of()));

        listener.onMessage(objectMapper.writeValueAsString(event), "user-events", event.userId().toString());

        verify(consumer).consume(event);
    }

    @Test
    void ignoresNonUserPreferencesUpdatedEventsOnSharedTopic() throws Exception {
        UserPreferencesUpdatedEventConsumer consumer = mock(UserPreferencesUpdatedEventConsumer.class);
        UserPreferencesUpdatedKafkaListener listener = new UserPreferencesUpdatedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<UserPreferencesUpdatedPayload> event = new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "GoalUpdated",
                1,
                Instant.parse("2026-06-12T00:20:00Z"),
                "user-service",
                UUID.randomUUID(),
                new UserPreferencesUpdatedPayload("100001", 4, "300003", true, List.of(), List.of()));

        listener.onMessage(objectMapper.writeValueAsString(event), "user-events", event.userId().toString());

        verifyNoInteractions(consumer);
    }
}
