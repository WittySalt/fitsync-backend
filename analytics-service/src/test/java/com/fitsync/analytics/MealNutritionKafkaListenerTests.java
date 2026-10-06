package com.fitsync.analytics;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.analytics.messaging.NutritionEventsKafkaListener;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.nutrition.MealDeletedPayload;
import com.fitsync.contracts.events.nutrition.MealLoggedPayload;
import com.fitsync.contracts.events.nutrition.MealUpdatedPayload;
import org.junit.jupiter.api.Test;

/** The one nutrition-topic listener dispatches on the event type and respects each type's own flag. */
class MealNutritionKafkaListenerTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @SuppressWarnings("unchecked")
    private final DomainEventConsumer<MealLoggedPayload> logged = mock(DomainEventConsumer.class);
    @SuppressWarnings("unchecked")
    private final DomainEventConsumer<MealUpdatedPayload> updated = mock(DomainEventConsumer.class);
    @SuppressWarnings("unchecked")
    private final DomainEventConsumer<MealDeletedPayload> deleted = mock(DomainEventConsumer.class);

    private NutritionEventsKafkaListener listener(boolean loggedOn, boolean updatedOn, boolean deletedOn) {
        return new NutritionEventsKafkaListener(objectMapper, logged, updated, deleted, loggedOn, updatedOn,
                deletedOn);
    }

    private static <T> FitSyncDomainEvent<T> event(String type, T payload) {
        return new FitSyncDomainEvent<>(UUID.randomUUID(), type, 1, Instant.parse("2026-06-14T04:00:00Z"),
                "nutrition-service", UUID.randomUUID(), payload);
    }

    private static MealLoggedPayload loggedPayload() {
        return new MealLoggedPayload(UUID.randomUUID(), "DINNER", Instant.parse("2026-06-14T03:50:00Z"),
                600, new BigDecimal("40.00"), new BigDecimal("52.00"), new BigDecimal("14.00"), 2, "MANUAL");
    }

    private void send(NutritionEventsKafkaListener listener, FitSyncDomainEvent<?> event) throws Exception {
        listener.onMessage(objectMapper.writeValueAsString(event), "nutrition-events", event.userId().toString());
    }

    @Test
    void dispatchesEachMealEventToItsOwnConsumer() throws Exception {
        NutritionEventsKafkaListener listener = listener(true, true, true);
        FitSyncDomainEvent<MealLoggedPayload> log = event("MealLogged", loggedPayload());
        FitSyncDomainEvent<MealUpdatedPayload> update = event("MealUpdated",
                new MealUpdatedPayload(UUID.randomUUID(), "DINNER", Instant.parse("2026-06-14T03:50:00Z"),
                        650, new BigDecimal("42.00"), new BigDecimal("52.00"), new BigDecimal("14.00"), 2, "MANUAL"));
        FitSyncDomainEvent<MealDeletedPayload> delete = event("MealDeleted",
                new MealDeletedPayload(UUID.randomUUID(), Instant.parse("2026-06-14T04:00:00Z")));

        send(listener, log);
        send(listener, update);
        send(listener, delete);

        verify(logged).consume(log);
        verify(updated).consume(update);
        verify(deleted).consume(delete);
    }

    @Test
    void ignoresOtherEventTypesOnTheSharedTopic() throws Exception {
        send(listener(true, true, true), event("WaterLogged", loggedPayload()));

        verifyNoInteractions(logged, updated, deleted);
    }

    @Test
    void routesATypeOnlyWhenItsOwnFlagIsOn() throws Exception {
        NutritionEventsKafkaListener listener = listener(false, false, true);

        send(listener, event("MealLogged", loggedPayload()));
        FitSyncDomainEvent<MealDeletedPayload> delete = event("MealDeleted",
                new MealDeletedPayload(UUID.randomUUID(), Instant.parse("2026-06-14T04:00:00Z")));
        send(listener, delete);

        verifyNoInteractions(logged, updated);
        verify(deleted).consume(delete);
    }
}
