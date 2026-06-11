package com.fitsync.nutrition;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.GoalUpdatedPayload;
import com.fitsync.nutrition.messaging.GoalUpdatedEventConsumer;
import com.fitsync.nutrition.messaging.GoalUpdatedKafkaListener;
import org.junit.jupiter.api.Test;

class GoalUpdatedKafkaListenerTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void consumesGoalUpdatedEventFromKafkaPayload() throws Exception {
        GoalUpdatedEventConsumer consumer = mock(GoalUpdatedEventConsumer.class);
        GoalUpdatedKafkaListener listener = new GoalUpdatedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<GoalUpdatedPayload> event = new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "GoalUpdated",
                1,
                Instant.parse("2026-06-11T14:00:00Z"),
                "user-service",
                UUID.randomUUID(),
                new GoalUpdatedPayload(
                        UUID.randomUUID(),
                        "CALORIES",
                        new BigDecimal("2100"),
                        "kcal",
                        LocalDate.parse("2026-09-01"),
                        "ACTIVE"));

        listener.onMessage(objectMapper.writeValueAsString(event), "progress-events", event.userId().toString());

        verify(consumer).consume(event);
    }

    @Test
    void ignoresNonGoalUpdatedEventsOnSharedTopic() throws Exception {
        GoalUpdatedEventConsumer consumer = mock(GoalUpdatedEventConsumer.class);
        GoalUpdatedKafkaListener listener = new GoalUpdatedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<GoalUpdatedPayload> event = new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "BodyMetricsRecorded",
                1,
                Instant.parse("2026-06-11T14:00:00Z"),
                "user-service",
                UUID.randomUUID(),
                new GoalUpdatedPayload(
                        UUID.randomUUID(),
                        "CALORIES",
                        new BigDecimal("2100"),
                        "kcal",
                        LocalDate.parse("2026-09-01"),
                        "ACTIVE"));

        listener.onMessage(objectMapper.writeValueAsString(event), "progress-events", event.userId().toString());

        verifyNoInteractions(consumer);
    }
}
