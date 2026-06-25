package com.fitsync.nutrition;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.AccountDeletedPayload;
import com.fitsync.nutrition.messaging.AccountDeletedKafkaListener;
import org.junit.jupiter.api.Test;

class AccountDeletedKafkaListenerTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void consumesAccountDeletedEventFromKafkaPayload() throws Exception {
        DomainEventConsumer<AccountDeletedPayload> consumer = mock(DomainEventConsumer.class);
        AccountDeletedKafkaListener listener = new AccountDeletedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<AccountDeletedPayload> event = event("AccountDeleted");

        listener.onMessage(objectMapper.writeValueAsString(event), "user-events", event.userId().toString());

        verify(consumer).consume(event);
    }

    @Test
    void ignoresOtherEventsOnSharedTopic() throws Exception {
        DomainEventConsumer<AccountDeletedPayload> consumer = mock(DomainEventConsumer.class);
        AccountDeletedKafkaListener listener = new AccountDeletedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<AccountDeletedPayload> event = event("UserPreferencesUpdated");

        listener.onMessage(objectMapper.writeValueAsString(event), "user-events", event.userId().toString());

        verifyNoInteractions(consumer);
    }

    private FitSyncDomainEvent<AccountDeletedPayload> event(String eventType) {
        return new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                eventType,
                1,
                Instant.parse("2026-06-25T04:00:00Z"),
                "user-service",
                UUID.randomUUID(),
                new AccountDeletedPayload(Instant.parse("2026-06-25T04:00:00Z"), "USER_REQUESTED"));
    }
}
