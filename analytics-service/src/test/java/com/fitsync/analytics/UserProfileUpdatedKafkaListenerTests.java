package com.fitsync.analytics;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.analytics.messaging.UserProfileUpdatedKafkaListener;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserProfileUpdatedPayload;
import org.junit.jupiter.api.Test;

class UserProfileUpdatedKafkaListenerTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @SuppressWarnings("unchecked")
    void consumesUserProfileUpdatedEvent() throws Exception {
        DomainEventConsumer<UserProfileUpdatedPayload> consumer = mock(DomainEventConsumer.class);
        UserProfileUpdatedKafkaListener listener = new UserProfileUpdatedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<UserProfileUpdatedPayload> event = event("UserProfileUpdated");

        listener.onMessage(objectMapper.writeValueAsString(event), "user-events", event.userId().toString());

        verify(consumer).consume(event);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ignoresOtherEventsOnSharedTopic() throws Exception {
        DomainEventConsumer<UserProfileUpdatedPayload> consumer = mock(DomainEventConsumer.class);
        UserProfileUpdatedKafkaListener listener = new UserProfileUpdatedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<UserProfileUpdatedPayload> event = event("GoalUpdated");

        listener.onMessage(objectMapper.writeValueAsString(event), "user-events", event.userId().toString());

        verifyNoInteractions(consumer);
    }

    private FitSyncDomainEvent<UserProfileUpdatedPayload> event(String eventType) {
        Instant at = Instant.parse("2026-06-25T04:00:00Z");
        return new FitSyncDomainEvent<>(
                UUID.randomUUID(), eventType, 1, at, "user-service", UUID.randomUUID(),
                new UserProfileUpdatedPayload("Ruixian", new BigDecimal("175.0"), "MODERATELY_ACTIVE",
                        true, List.of("timezone"), "Asia/Singapore"));
    }
}
