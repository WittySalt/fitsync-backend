package com.fitsync.nutrition;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.BodyMetricsRecordedPayload;
import com.fitsync.nutrition.messaging.BodyMetricsRecordedKafkaListener;
import org.junit.jupiter.api.Test;

class BodyMetricsRecordedKafkaListenerTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private FitSyncDomainEvent<BodyMetricsRecordedPayload> event(String type) {
        return new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                type,
                1,
                Instant.parse("2026-06-12T00:20:00Z"),
                "user-service",
                UUID.randomUUID(),
                new BodyMetricsRecordedPayload(UUID.randomUUID(), new java.math.BigDecimal("70.5"), null, null, Instant.parse("2026-06-12T00:10:00Z")));
    }

    @Test
    void consumesBodyMetricsRecordedEventFromKafkaPayload() throws Exception {
        DomainEventConsumer<BodyMetricsRecordedPayload> consumer = mock(DomainEventConsumer.class);
        BodyMetricsRecordedKafkaListener listener = new BodyMetricsRecordedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<BodyMetricsRecordedPayload> event = event("BodyMetricsRecorded");

        listener.onMessage(objectMapper.writeValueAsString(event), "progress-events", event.userId().toString());

        verify(consumer).consume(event);
    }

    @Test
    void ignoresOtherEventTypesOnTheSharedTopic() throws Exception {
        DomainEventConsumer<BodyMetricsRecordedPayload> consumer = mock(DomainEventConsumer.class);
        BodyMetricsRecordedKafkaListener listener = new BodyMetricsRecordedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<BodyMetricsRecordedPayload> event = event("GoalUpdated");

        listener.onMessage(objectMapper.writeValueAsString(event), "progress-events", event.userId().toString());

        verifyNoInteractions(consumer);
    }
}
