package com.fitsync.web.messaging;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.AccountDeletedPayload;
import org.junit.jupiter.api.Test;

class AbstractDomainEventKafkaListenerTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static final class TestListener extends AbstractDomainEventKafkaListener<AccountDeletedPayload> {
        TestListener(ObjectMapper objectMapper, DomainEventConsumer<AccountDeletedPayload> consumer) {
            super(objectMapper, AccountDeletedPayload.class, "AccountDeleted", consumer);
        }

        void receive(String payload, String topic, String key) {
            handle(payload, topic, key);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatchesMatchingEventToConsumer() throws Exception {
        DomainEventConsumer<AccountDeletedPayload> consumer = mock(DomainEventConsumer.class);
        TestListener listener = new TestListener(objectMapper, consumer);
        FitSyncDomainEvent<AccountDeletedPayload> event = event("AccountDeleted");

        listener.receive(objectMapper.writeValueAsString(event), "user-events", event.userId().toString());

        verify(consumer).consume(event);
    }

    @Test
    @SuppressWarnings("unchecked")
    void toleratesUnknownEventAndPayloadFieldsEvenWithStrictObjectMapper() throws Exception {
        DomainEventConsumer<AccountDeletedPayload> consumer = mock(DomainEventConsumer.class);
        ObjectMapper strictObjectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        TestListener listener = new TestListener(strictObjectMapper, consumer);
        String payload = """
                {
                  "eventId": "11111111-1111-1111-1111-111111111111",
                  "eventType": "AccountDeleted",
                  "version": 1,
                  "occurredAt": "2026-06-25T04:00:00Z",
                  "source": "user-service",
                  "userId": "22222222-2222-2222-2222-222222222222",
                  "futureEnvelopeField": "ignored",
                  "payload": {
                    "deletedAt": "2026-06-25T04:00:00Z",
                    "reason": "USER_REQUESTED",
                    "futurePayloadField": "ignored"
                  }
                }
                """;
        FitSyncDomainEvent<AccountDeletedPayload> expected = new FitSyncDomainEvent<>(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "AccountDeleted",
                1,
                Instant.parse("2026-06-25T04:00:00Z"),
                "user-service",
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                new AccountDeletedPayload(Instant.parse("2026-06-25T04:00:00Z"), "USER_REQUESTED"));

        listener.receive(payload, "user-events", expected.userId().toString());

        verify(consumer).consume(expected);
    }

    @Test
    @SuppressWarnings("unchecked")
    void skipsEventsOfADifferentType() throws Exception {
        DomainEventConsumer<AccountDeletedPayload> consumer = mock(DomainEventConsumer.class);
        TestListener listener = new TestListener(objectMapper, consumer);
        FitSyncDomainEvent<AccountDeletedPayload> event = event("GoalUpdated");

        listener.receive(objectMapper.writeValueAsString(event), "user-events\r\ninjected", null);

        verifyNoInteractions(consumer);
    }

    @Test
    @SuppressWarnings("unchecked")
    void throwsOnMalformedPayload() {
        DomainEventConsumer<AccountDeletedPayload> consumer = mock(DomainEventConsumer.class);
        TestListener listener = new TestListener(objectMapper, consumer);

        assertThrows(IllegalArgumentException.class,
                () -> listener.receive("{not-json", "user-events", "key"));
        verifyNoInteractions(consumer);
    }

    private FitSyncDomainEvent<AccountDeletedPayload> event(String eventType) {
        Instant at = Instant.parse("2026-06-25T04:00:00Z");
        return new FitSyncDomainEvent<>(
                UUID.randomUUID(), eventType, 1, at, "user-service", UUID.randomUUID(),
                new AccountDeletedPayload(at, "USER_REQUESTED"));
    }
}
