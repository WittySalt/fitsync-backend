package com.fitsync.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsProcessedEventEntity;
import com.fitsync.analytics.entity.AnalyticsUserWorkoutRecordsEntity;
import com.fitsync.analytics.messaging.WorkoutRecordsProjectionService;
import com.fitsync.analytics.repository.AnalyticsProcessedEventRepository;
import com.fitsync.analytics.repository.AnalyticsUserWorkoutRecordsRepository;
import com.fitsync.analytics.service.AnalyticsCacheInvalidationService;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.workout.PersonalRecordAchievedPayload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkoutRecordsProjectionServiceTests {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-06-14T08:00:00Z");

    private final AnalyticsProcessedEventRepository processedEventRepository =
            mock(AnalyticsProcessedEventRepository.class);
    private final AnalyticsUserWorkoutRecordsRepository workoutRecordsRepository =
            mock(AnalyticsUserWorkoutRecordsRepository.class);
    private final AnalyticsCacheInvalidationService cacheInvalidationService =
            mock(AnalyticsCacheInvalidationService.class);
    private final WorkoutRecordsProjectionService service = new WorkoutRecordsProjectionService(
            processedEventRepository, workoutRecordsRepository, cacheInvalidationService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsRecordsRowOnFirstPersonalRecord() {
        FitSyncDomainEvent<PersonalRecordAchievedPayload> event = event(UUID.randomUUID());
        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);
        when(workoutRecordsRepository.findById(USER_ID)).thenReturn(Optional.empty());

        service.applyPersonalRecords(event, 2, Instant.parse("2026-06-14T03:45:00Z"));

        AnalyticsUserWorkoutRecordsEntity saved = savedRecords();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getPersonalRecordCount()).isEqualTo(2);
        assertThat(saved.getFirstRecordAt()).isEqualTo(Instant.parse("2026-06-14T03:45:00Z"));
        assertThat(saved.getLastRecordAt()).isEqualTo(Instant.parse("2026-06-14T03:45:00Z"));
        verify(processedEventRepository).save(processedEvent());
        verify(cacheInvalidationService).evictForUser(USER_ID);
    }

    @Test
    void accumulatesCountAndKeepsFirstRecordTimestamp() {
        FitSyncDomainEvent<PersonalRecordAchievedPayload> event = event(UUID.randomUUID());
        AnalyticsUserWorkoutRecordsEntity existing = new AnalyticsUserWorkoutRecordsEntity();
        existing.setUserId(USER_ID);
        existing.setPersonalRecordCount(3);
        existing.setFirstRecordAt(Instant.parse("2026-05-01T10:00:00Z"));
        existing.setLastRecordAt(Instant.parse("2026-05-20T10:00:00Z"));
        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);
        when(workoutRecordsRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

        service.applyPersonalRecords(event, 1, Instant.parse("2026-06-14T03:45:00Z"));

        AnalyticsUserWorkoutRecordsEntity saved = savedRecords();
        assertThat(saved.getPersonalRecordCount()).isEqualTo(4);
        assertThat(saved.getFirstRecordAt()).isEqualTo(Instant.parse("2026-05-01T10:00:00Z"));
        assertThat(saved.getLastRecordAt()).isEqualTo(Instant.parse("2026-06-14T03:45:00Z"));
    }

    @Test
    void ignoresDuplicateEvent() {
        FitSyncDomainEvent<PersonalRecordAchievedPayload> event = event(UUID.randomUUID());
        when(processedEventRepository.existsById(event.eventId())).thenReturn(true);

        service.applyPersonalRecords(event, 1, Instant.parse("2026-06-14T03:45:00Z"));

        verify(workoutRecordsRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(processedEventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordsProcessedEventWithoutRowForEmptyRecordList() {
        FitSyncDomainEvent<PersonalRecordAchievedPayload> event = event(UUID.randomUUID());
        when(processedEventRepository.existsById(event.eventId())).thenReturn(false);

        service.applyPersonalRecords(event, 0, Instant.parse("2026-06-14T03:45:00Z"));

        verify(workoutRecordsRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(processedEventRepository).save(processedEvent());
    }

    private AnalyticsUserWorkoutRecordsEntity savedRecords() {
        ArgumentCaptor<AnalyticsUserWorkoutRecordsEntity> captor =
                ArgumentCaptor.forClass(AnalyticsUserWorkoutRecordsEntity.class);
        verify(workoutRecordsRepository).save(captor.capture());
        return captor.getValue();
    }

    private AnalyticsProcessedEventEntity processedEvent() {
        return org.mockito.ArgumentMatchers.any(AnalyticsProcessedEventEntity.class);
    }

    private FitSyncDomainEvent<PersonalRecordAchievedPayload> event(UUID eventId) {
        return new FitSyncDomainEvent<>(
                eventId,
                "PersonalRecordAchieved",
                1,
                Instant.parse("2026-06-14T04:00:00Z"),
                "workout-service",
                USER_ID,
                null);
    }
}
