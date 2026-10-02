package com.fitsync.analytics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.analytics.messaging.EnergyProfileComputedKafkaListener;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.EnergyProfileComputedPayload;
import org.junit.jupiter.api.Test;

class EnergyProfileComputedKafkaListenerTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @SuppressWarnings("unchecked")
    void consumesEnergyProfileComputedAndKeepsTheEstimatedFlag() throws Exception {
        DomainEventConsumer<EnergyProfileComputedPayload> consumer = mock(DomainEventConsumer.class);
        EnergyProfileComputedKafkaListener listener = new EnergyProfileComputedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<EnergyProfileComputedPayload> event = event("EnergyProfileComputed");

        listener.onMessage(objectMapper.writeValueAsString(event), "user-events", event.userId().toString());

        verify(consumer).consume(event);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ignoresOtherEventsOnTheSharedUserTopic() throws Exception {
        DomainEventConsumer<EnergyProfileComputedPayload> consumer = mock(DomainEventConsumer.class);
        EnergyProfileComputedKafkaListener listener = new EnergyProfileComputedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<EnergyProfileComputedPayload> event = event("UserProfileUpdated");

        listener.onMessage(objectMapper.writeValueAsString(event), "user-events", event.userId().toString());

        verifyNoInteractions(consumer);
    }

    @Test
    @SuppressWarnings("unchecked")
    void readsAnEventFromBeforeTheEstimatedFlagAndIgnoresUnknownFields() {
        DomainEventConsumer<EnergyProfileComputedPayload> consumer = mock(DomainEventConsumer.class);
        EnergyProfileComputedKafkaListener listener = new EnergyProfileComputedKafkaListener(objectMapper, consumer);
        String json = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"EnergyProfileComputed\","
                + "\"version\":1,\"occurredAt\":\"2026-09-01T08:00:00Z\",\"source\":\"user-service\","
                + "\"userId\":\"" + UUID.randomUUID() + "\",\"payload\":{\"bmrCalories\":1662,"
                + "\"tdeeCalories\":2576,\"activityLevel\":\"MODERATE\",\"computedAt\":\"2026-09-01T08:00:00Z\","
                + "\"someFutureField\":true}}";

        listener.onMessage(json, "user-events", null);

        verify(consumer).consume(any());
    }

    private FitSyncDomainEvent<EnergyProfileComputedPayload> event(String eventType) {
        Instant at = Instant.parse("2026-09-01T08:00:00Z");
        return new FitSyncDomainEvent<>(UUID.randomUUID(), eventType, 1, at, "user-service", UUID.randomUUID(),
                new EnergyProfileComputedPayload(1662, 2576, "MODERATE", at, true));
    }
}
