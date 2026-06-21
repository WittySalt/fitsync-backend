package com.fitsync.analytics;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.UUID;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.analytics.messaging.WorkoutDeletedKafkaListener;
import com.fitsync.analytics.messaging.WorkoutLoggedKafkaListener;
import com.fitsync.analytics.messaging.WorkoutUpdatedKafkaListener;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.workout.WorkoutDeletedPayload;
import com.fitsync.contracts.events.workout.WorkoutLoggedPayload;
import com.fitsync.contracts.events.workout.WorkoutUpdatedPayload;
import org.junit.jupiter.api.Test;

class WorkoutKafkaListenerTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void workoutLoggedListenerConsumesMatchingEventType() throws Exception {
        DomainEventConsumer<WorkoutLoggedPayload> consumer = mock(DomainEventConsumer.class);
        WorkoutLoggedKafkaListener listener = new WorkoutLoggedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<WorkoutLoggedPayload> event = new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "WorkoutLogged",
                1,
                Instant.parse("2026-06-14T04:00:00Z"),
                "workout-service",
                UUID.randomUUID(),
                new WorkoutLoggedPayload(UUID.randomUUID(), null, "STRENGTH", "COMPLETED",
                        Instant.parse("2026-06-14T03:00:00Z"), Instant.parse("2026-06-14T03:45:00Z"),
                        45, 350, 6, "PLAN"));

        listener.onMessage(objectMapper.writeValueAsString(event), "workout-events", event.userId().toString());

        verify(consumer).consume(event);
    }

    @Test
    void workoutUpdatedListenerIgnoresOtherEventTypesOnSharedTopic() throws Exception {
        DomainEventConsumer<WorkoutUpdatedPayload> consumer = mock(DomainEventConsumer.class);
        WorkoutUpdatedKafkaListener listener = new WorkoutUpdatedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<WorkoutUpdatedPayload> event = new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "WorkoutLogged",
                1,
                Instant.parse("2026-06-14T04:00:00Z"),
                "workout-service",
                UUID.randomUUID(),
                new WorkoutUpdatedPayload(UUID.randomUUID(), null, "STRENGTH", "COMPLETED",
                        Instant.parse("2026-06-14T03:00:00Z"), Instant.parse("2026-06-14T03:45:00Z"),
                        45, 350, 6, "PLAN"));

        listener.onMessage(objectMapper.writeValueAsString(event), "workout-events", event.userId().toString());

        verifyNoInteractions(consumer);
    }

    @Test
    void workoutDeletedListenerConsumesMatchingEventType() throws Exception {
        DomainEventConsumer<WorkoutDeletedPayload> consumer = mock(DomainEventConsumer.class);
        WorkoutDeletedKafkaListener listener = new WorkoutDeletedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<WorkoutDeletedPayload> event = new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "WorkoutDeleted",
                1,
                Instant.parse("2026-06-14T04:00:00Z"),
                "workout-service",
                UUID.randomUUID(),
                new WorkoutDeletedPayload(UUID.randomUUID(), null, Instant.parse("2026-06-14T04:00:00Z")));

        listener.onMessage(objectMapper.writeValueAsString(event), "workout-events", event.userId().toString());

        verify(consumer).consume(event);
    }
}
