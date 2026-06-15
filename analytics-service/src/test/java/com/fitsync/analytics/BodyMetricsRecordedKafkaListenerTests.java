package com.fitsync.analytics;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.analytics.messaging.BodyMetricsRecordedEventConsumer;
import com.fitsync.analytics.messaging.BodyMetricsRecordedKafkaListener;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.BodyMetricsRecordedPayload;
import org.junit.jupiter.api.Test;

class BodyMetricsRecordedKafkaListenerTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void consumesBodyMetricsRecordedEventFromKafkaPayload() throws Exception {
        BodyMetricsRecordedEventConsumer consumer = mock(BodyMetricsRecordedEventConsumer.class);
        BodyMetricsRecordedKafkaListener listener = new BodyMetricsRecordedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<BodyMetricsRecordedPayload> event = new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "BodyMetricsRecorded",
                1,
                Instant.parse("2026-06-14T04:00:00Z"),
                "user-service",
                UUID.randomUUID(),
                new BodyMetricsRecordedPayload(
                        UUID.randomUUID(),
                        new BigDecimal("78.40"),
                        new BigDecimal("19.80"),
                        new BigDecimal("85.50"),
                        Instant.parse("2026-06-14T03:50:00Z")));

        listener.onMessage(objectMapper.writeValueAsString(event), "progress-events", event.userId().toString());

        verify(consumer).consume(event);
    }

    @Test
    void ignoresNonBodyMetricsRecordedEventsOnSharedTopic() throws Exception {
        BodyMetricsRecordedEventConsumer consumer = mock(BodyMetricsRecordedEventConsumer.class);
        BodyMetricsRecordedKafkaListener listener = new BodyMetricsRecordedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<BodyMetricsRecordedPayload> event = new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "GoalUpdated",
                1,
                Instant.parse("2026-06-14T04:00:00Z"),
                "user-service",
                UUID.randomUUID(),
                new BodyMetricsRecordedPayload(
                        UUID.randomUUID(),
                        new BigDecimal("78.40"),
                        new BigDecimal("19.80"),
                        new BigDecimal("85.50"),
                        Instant.parse("2026-06-14T03:50:00Z")));

        listener.onMessage(objectMapper.writeValueAsString(event), "progress-events", event.userId().toString());

        verifyNoInteractions(consumer);
    }
}
