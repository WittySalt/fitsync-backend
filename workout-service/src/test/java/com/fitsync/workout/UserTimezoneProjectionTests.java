package com.fitsync.workout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserPreferencesUpdatedPayload;
import com.fitsync.contracts.events.user.UserProfileUpdatedPayload;
import com.fitsync.workout.entity.UserTimezoneEntity;
import com.fitsync.workout.messaging.UserPreferencesUpdatedEventConsumerImpl;
import com.fitsync.workout.messaging.UserPreferencesUpdatedKafkaListener;
import com.fitsync.workout.messaging.UserProfileUpdatedEventConsumerImpl;
import com.fitsync.workout.messaging.UserProfileUpdatedKafkaListener;
import com.fitsync.workout.messaging.UserTimezoneProjectionService;
import com.fitsync.workout.repository.UserTimezoneRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UserTimezoneProjectionTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final UserTimezoneRepository repository = mock(UserTimezoneRepository.class);
    private final UserTimezoneProjectionService service = new UserTimezoneProjectionService(
            repository, Clock.fixed(Instant.parse("2026-10-03T00:00:00Z"), ZoneOffset.UTC));
    private final UUID userId = UUID.randomUUID();

    @Test
    void storesTheZoneOfAProfileEventForANewUser() {
        when(repository.findById(userId)).thenReturn(Optional.empty());

        new UserProfileUpdatedEventConsumerImpl(service).consume(profileEvent("Asia/Singapore", "2026-10-01T00:00:00Z"));

        ArgumentCaptor<UserTimezoneEntity> saved = ArgumentCaptor.forClass(UserTimezoneEntity.class);
        verify(repository).save(saved.capture());
        assertEquals(userId, saved.getValue().getUserId());
        assertEquals("Asia/Singapore", saved.getValue().getTimezone());
    }

    @Test
    void aPreferencesEventUpdatesTheStoredZone() {
        when(repository.findById(userId)).thenReturn(Optional.of(
                new UserTimezoneEntity(userId, "UTC", Instant.parse("2026-09-01T00:00:00Z"))));

        new UserPreferencesUpdatedEventConsumerImpl(service)
                .consume(preferencesEvent("America/Los_Angeles", "2026-10-02T00:00:00Z"));

        ArgumentCaptor<UserTimezoneEntity> saved = ArgumentCaptor.forClass(UserTimezoneEntity.class);
        verify(repository).save(saved.capture());
        assertEquals("America/Los_Angeles", saved.getValue().getTimezone());
        assertEquals(Instant.parse("2026-10-02T00:00:00Z"), saved.getValue().getUpdatedAt());
    }

    @Test
    void anEventOlderThanTheStoredZoneIsIgnored() {
        when(repository.findById(userId)).thenReturn(Optional.of(
                new UserTimezoneEntity(userId, "Asia/Singapore", Instant.parse("2026-10-02T00:00:00Z"))));

        service.projectTimezone(profileEvent("UTC", "2026-09-01T00:00:00Z"), "UTC");

        verify(repository, never()).save(any());
    }

    @Test
    void blankAndUnknownZonesAreIgnored() {
        service.projectTimezone(profileEvent(null, "2026-10-01T00:00:00Z"), null);
        service.projectTimezone(profileEvent(" ", "2026-10-01T00:00:00Z"), " ");
        service.projectTimezone(profileEvent("Mars/Base", "2026-10-01T00:00:00Z"), "Mars/Base");

        verifyNoInteractions(repository);
    }

    @Test
    void listenersConsumeOnlyTheirOwnEventType() throws Exception {
        DomainEventConsumer<UserProfileUpdatedPayload> profileConsumer = mock(DomainEventConsumer.class);
        UserProfileUpdatedKafkaListener profileListener =
                new UserProfileUpdatedKafkaListener(objectMapper, profileConsumer);
        FitSyncDomainEvent<UserProfileUpdatedPayload> profile = profileEvent("Asia/Singapore", "2026-10-01T00:00:00Z");
        profileListener.onMessage(objectMapper.writeValueAsString(profile), "user-events", userId.toString());
        verify(profileConsumer).consume(profile);

        DomainEventConsumer<UserPreferencesUpdatedPayload> preferencesConsumer = mock(DomainEventConsumer.class);
        UserPreferencesUpdatedKafkaListener preferencesListener =
                new UserPreferencesUpdatedKafkaListener(objectMapper, preferencesConsumer);
        preferencesListener.onMessage(objectMapper.writeValueAsString(profile), "user-events", userId.toString());
        verifyNoInteractions(preferencesConsumer);
    }

    private FitSyncDomainEvent<UserProfileUpdatedPayload> profileEvent(String timezone, String occurredAt) {
        return new FitSyncDomainEvent<>(UUID.randomUUID(), "UserProfileUpdated", 1, Instant.parse(occurredAt),
                "user-service", userId,
                new UserProfileUpdatedPayload("Alice", null, null, true, List.of("timezone"), timezone));
    }

    private FitSyncDomainEvent<UserPreferencesUpdatedPayload> preferencesEvent(String timezone, String occurredAt) {
        return new FitSyncDomainEvent<>(UUID.randomUUID(), "UserPreferencesUpdated", 1, Instant.parse(occurredAt),
                "user-service", userId,
                new UserPreferencesUpdatedPayload("METRIC", 4, null, true, List.of(), List.of(), timezone));
    }
}
