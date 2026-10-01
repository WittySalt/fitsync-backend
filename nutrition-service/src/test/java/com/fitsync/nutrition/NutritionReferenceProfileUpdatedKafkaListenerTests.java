package com.fitsync.nutrition;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.NutritionReferenceProfileUpdatedPayload;
import com.fitsync.nutrition.messaging.NutritionReferenceProfileUpdatedKafkaListener;
import org.junit.jupiter.api.Test;

class NutritionReferenceProfileUpdatedKafkaListenerTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private FitSyncDomainEvent<NutritionReferenceProfileUpdatedPayload> event(String type) {
        return new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                type,
                1,
                Instant.parse("2026-06-12T00:20:00Z"),
                "user-service",
                UUID.randomUUID(),
                new NutritionReferenceProfileUpdatedPayload("FEMALE", "31-50"));
    }

    @Test
    void consumesNutritionReferenceProfileUpdatedEventFromKafkaPayload() throws Exception {
        DomainEventConsumer<NutritionReferenceProfileUpdatedPayload> consumer = mock(DomainEventConsumer.class);
        NutritionReferenceProfileUpdatedKafkaListener listener = new NutritionReferenceProfileUpdatedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<NutritionReferenceProfileUpdatedPayload> event = event("NutritionReferenceProfileUpdated");

        listener.onMessage(objectMapper.writeValueAsString(event), "user-events", event.userId().toString());

        verify(consumer).consume(event);
    }

    @Test
    void ignoresOtherEventTypesOnTheSharedTopic() throws Exception {
        DomainEventConsumer<NutritionReferenceProfileUpdatedPayload> consumer = mock(DomainEventConsumer.class);
        NutritionReferenceProfileUpdatedKafkaListener listener = new NutritionReferenceProfileUpdatedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<NutritionReferenceProfileUpdatedPayload> event = event("GoalUpdated");

        listener.onMessage(objectMapper.writeValueAsString(event), "user-events", event.userId().toString());

        verifyNoInteractions(consumer);
    }
}
