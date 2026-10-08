package com.fitsync.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserRegisteredPayload;
import com.fitsync.user.messaging.UserRegisteredEventConsumer;
import com.fitsync.user.messaging.UserRegisteredKafkaListener;
import org.junit.jupiter.api.Test;

class UserRegisteredKafkaListenerTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void consumesUserRegisteredEventFromKafkaPayload() throws Exception {
        UserRegisteredEventConsumer consumer = mock(UserRegisteredEventConsumer.class);
        UserRegisteredKafkaListener listener = new UserRegisteredKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<UserRegisteredPayload> event = new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "UserRegistered",
                1,
                Instant.parse("2026-06-11T14:00:00Z"),
                "auth-service",
                UUID.randomUUID(),
                new UserRegisteredPayload(
                        "listener@example.com",
                        "Listener User",
                        "USER",
                        Instant.parse("2026-06-11T14:00:00Z")));

        listener.onMessage(objectMapper.writeValueAsString(event), "user-events", event.userId().toString());

        verify(consumer).consume(event);
    }

    @Test
    void ignoresNonUserRegisteredEventsOnSharedTopic() throws Exception {
        UserRegisteredEventConsumer consumer = mock(UserRegisteredEventConsumer.class);
        UserRegisteredKafkaListener listener = new UserRegisteredKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<UserRegisteredPayload> event = new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "UserProfileUpdated",
                1,
                Instant.parse("2026-06-11T14:00:00Z"),
                "user-service",
                UUID.randomUUID(),
                new UserRegisteredPayload(
                        "listener@example.com",
                        "Listener User",
                        "USER",
                        Instant.parse("2026-06-11T14:00:00Z")));

        listener.onMessage(objectMapper.writeValueAsString(event), "user-events", event.userId().toString());

        verifyNoInteractions(consumer);
    }

    @Test
    void rejectsAPayloadThatIsNotAnEvent() {
        UserRegisteredEventConsumer consumer = mock(UserRegisteredEventConsumer.class);
        UserRegisteredKafkaListener listener = new UserRegisteredKafkaListener(objectMapper, consumer);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> listener.onMessage("{not json", "user-events", null));

        assertEquals("Failed to deserialize UserRegistered event", error.getMessage());
        verifyNoInteractions(consumer);
    }
}
