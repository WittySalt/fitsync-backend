package com.fitsync.analytics;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.analytics.messaging.MealDeletedKafkaListener;
import com.fitsync.analytics.messaging.MealLoggedKafkaListener;
import com.fitsync.analytics.messaging.MealUpdatedKafkaListener;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.nutrition.MealDeletedPayload;
import com.fitsync.contracts.events.nutrition.MealLoggedPayload;
import com.fitsync.contracts.events.nutrition.MealUpdatedPayload;
import org.junit.jupiter.api.Test;

class MealNutritionKafkaListenerTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void mealLoggedListenerConsumesMatchingEventType() throws Exception {
        DomainEventConsumer<MealLoggedPayload> consumer = mock(DomainEventConsumer.class);
        MealLoggedKafkaListener listener = new MealLoggedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<MealLoggedPayload> event = new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "MealLogged",
                1,
                Instant.parse("2026-06-14T04:00:00Z"),
                "nutrition-service",
                UUID.randomUUID(),
                new MealLoggedPayload(UUID.randomUUID(), "DINNER", Instant.parse("2026-06-14T03:50:00Z"),
                        600, new BigDecimal("40.00"), new BigDecimal("52.00"), new BigDecimal("14.00"), 2, "MANUAL"));

        listener.onMessage(objectMapper.writeValueAsString(event), "nutrition-events", event.userId().toString());

        verify(consumer).consume(event);
    }

    @Test
    void mealUpdatedListenerIgnoresOtherEventTypesOnSharedTopic() throws Exception {
        DomainEventConsumer<MealUpdatedPayload> consumer = mock(DomainEventConsumer.class);
        MealUpdatedKafkaListener listener = new MealUpdatedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<MealUpdatedPayload> event = new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "MealLogged",
                1,
                Instant.parse("2026-06-14T04:00:00Z"),
                "nutrition-service",
                UUID.randomUUID(),
                new MealUpdatedPayload(UUID.randomUUID(), "DINNER", Instant.parse("2026-06-14T03:50:00Z"),
                        600, new BigDecimal("40.00"), new BigDecimal("52.00"), new BigDecimal("14.00"), 2, "MANUAL"));

        listener.onMessage(objectMapper.writeValueAsString(event), "nutrition-events", event.userId().toString());

        verifyNoInteractions(consumer);
    }

    @Test
    void mealDeletedListenerConsumesMatchingEventType() throws Exception {
        DomainEventConsumer<MealDeletedPayload> consumer = mock(DomainEventConsumer.class);
        MealDeletedKafkaListener listener = new MealDeletedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<MealDeletedPayload> event = new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "MealDeleted",
                1,
                Instant.parse("2026-06-14T04:00:00Z"),
                "nutrition-service",
                UUID.randomUUID(),
                new MealDeletedPayload(UUID.randomUUID(), Instant.parse("2026-06-14T04:00:00Z")));

        listener.onMessage(objectMapper.writeValueAsString(event), "nutrition-events", event.userId().toString());

        verify(consumer).consume(event);
    }
}
