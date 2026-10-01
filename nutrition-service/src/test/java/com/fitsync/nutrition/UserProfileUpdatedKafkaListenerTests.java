package com.fitsync.nutrition;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserProfileUpdatedPayload;
import com.fitsync.nutrition.messaging.UserProfileUpdatedKafkaListener;
import org.junit.jupiter.api.Test;

class UserProfileUpdatedKafkaListenerTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private FitSyncDomainEvent<UserProfileUpdatedPayload> event(String type) {
        return new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                type,
                1,
                Instant.parse("2026-06-12T00:20:00Z"),
                "user-service",
                UUID.randomUUID(),
                new UserProfileUpdatedPayload("Alice", new java.math.BigDecimal("170.0"), "MODERATELY_ACTIVE", true, List.of(), "Asia/Singapore", "FEMALE", java.time.LocalDate.parse("1995-04-12")));
    }

    @Test
    void consumesUserProfileUpdatedEventFromKafkaPayload() throws Exception {
        DomainEventConsumer<UserProfileUpdatedPayload> consumer = mock(DomainEventConsumer.class);
        UserProfileUpdatedKafkaListener listener = new UserProfileUpdatedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<UserProfileUpdatedPayload> event = event("UserProfileUpdated");

        listener.onMessage(objectMapper.writeValueAsString(event), "user-events", event.userId().toString());

        verify(consumer).consume(event);
    }

    @Test
    void ignoresOtherEventTypesOnTheSharedTopic() throws Exception {
        DomainEventConsumer<UserProfileUpdatedPayload> consumer = mock(DomainEventConsumer.class);
        UserProfileUpdatedKafkaListener listener = new UserProfileUpdatedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<UserProfileUpdatedPayload> event = event("GoalUpdated");

        listener.onMessage(objectMapper.writeValueAsString(event), "user-events", event.userId().toString());

        verifyNoInteractions(consumer);
    }
}
