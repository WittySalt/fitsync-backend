package com.fitsync.analytics;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.analytics.messaging.PersonalRecordAchievedKafkaListener;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.workout.PersonalRecordAchievedPayload;
import org.junit.jupiter.api.Test;

class PersonalRecordAchievedKafkaListenerTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void personalRecordAchievedListenerConsumesMatchingEventType() throws Exception {
        DomainEventConsumer<PersonalRecordAchievedPayload> consumer = mock(DomainEventConsumer.class);
        PersonalRecordAchievedKafkaListener listener = new PersonalRecordAchievedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<PersonalRecordAchievedPayload> event = new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "PersonalRecordAchieved",
                1,
                Instant.parse("2026-06-14T04:00:00Z"),
                "workout-service",
                UUID.randomUUID(),
                new PersonalRecordAchievedPayload(
                        UUID.randomUUID(),
                        "Back Squat",
                        UUID.randomUUID(),
                        List.of(new PersonalRecordAchievedPayload.RecordAchievement("MAX_WEIGHT_KG", new BigDecimal("120.00"))),
                        Instant.parse("2026-06-14T03:45:00Z")));

        listener.onMessage(objectMapper.writeValueAsString(event), "workout-events", event.userId().toString());

        verify(consumer).consume(event);
    }

    @Test
    void personalRecordAchievedListenerIgnoresOtherEventTypesOnSharedTopic() throws Exception {
        DomainEventConsumer<PersonalRecordAchievedPayload> consumer = mock(DomainEventConsumer.class);
        PersonalRecordAchievedKafkaListener listener = new PersonalRecordAchievedKafkaListener(objectMapper, consumer);
        FitSyncDomainEvent<PersonalRecordAchievedPayload> event = new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "WorkoutLogged",
                1,
                Instant.parse("2026-06-14T04:00:00Z"),
                "workout-service",
                UUID.randomUUID(),
                new PersonalRecordAchievedPayload(
                        UUID.randomUUID(),
                        "Back Squat",
                        UUID.randomUUID(),
                        List.of(),
                        Instant.parse("2026-06-14T03:45:00Z")));

        listener.onMessage(objectMapper.writeValueAsString(event), "workout-events", event.userId().toString());

        verifyNoInteractions(consumer);
    }
}
