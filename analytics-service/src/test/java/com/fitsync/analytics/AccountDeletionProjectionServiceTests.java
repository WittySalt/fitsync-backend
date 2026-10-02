package com.fitsync.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsProcessedEventEntity;
import com.fitsync.analytics.messaging.AccountDeletionProjectionService;
import com.fitsync.analytics.repository.AnalyticsMealContributionRepository;
import com.fitsync.analytics.repository.AnalyticsProcessedEventRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import com.fitsync.analytics.repository.AnalyticsUserStreakRollupRepository;
import com.fitsync.analytics.repository.AnalyticsUserWorkoutRecordsRepository;
import com.fitsync.analytics.repository.AnalyticsUserEnergyProfileRepository;
import com.fitsync.analytics.repository.AnalyticsWorkoutContributionRepository;
import com.fitsync.analytics.service.AnalyticsCacheInvalidationService;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.AccountDeletedPayload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AccountDeletionProjectionServiceTests {

    @Test
    void deletesAnalyticsRowsAndRecordsAccountDeletedEvent() {
        Fixtures fixtures = new Fixtures();
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FitSyncDomainEvent<AccountDeletedPayload> event = event(eventId, userId);
        when(fixtures.processedEventRepository.existsById(eventId)).thenReturn(false);

        fixtures.service.deleteUserData(event);

        verify(fixtures.mealContributionRepository).deleteByUserId(userId);
        verify(fixtures.workoutContributionRepository).deleteByUserId(userId);
        verify(fixtures.dailyBodyRepository).deleteByUserId(userId);
        verify(fixtures.dailyNutritionRepository).deleteByUserId(userId);
        verify(fixtures.dailyWorkoutRepository).deleteByUserId(userId);
        verify(fixtures.goalProgressRepository).deleteByUserId(userId);
        verify(fixtures.dashboardSnapshotRepository).deleteById(userId);
        verify(fixtures.streakRollupRepository).deleteById(userId);
        verify(fixtures.workoutRecordsRepository).deleteById(userId);
        verify(fixtures.energyProfileRepository).deleteByUserId(userId);
        verify(fixtures.processedEventRepository).deleteByUserId(userId);
        verify(fixtures.cacheInvalidationService).evictForUser(userId);

        ArgumentCaptor<AnalyticsProcessedEventEntity> processedCaptor =
                ArgumentCaptor.forClass(AnalyticsProcessedEventEntity.class);
        verify(fixtures.processedEventRepository).save(processedCaptor.capture());
        assertEquals(eventId, processedCaptor.getValue().getEventId());
        assertEquals("AccountDeleted", processedCaptor.getValue().getEventType());
        assertEquals(userId, processedCaptor.getValue().getUserId());
    }

    @Test
    void ignoresDuplicateDeletionEvent() {
        Fixtures fixtures = new Fixtures();
        UUID eventId = UUID.randomUUID();
        FitSyncDomainEvent<AccountDeletedPayload> event = event(eventId, UUID.randomUUID());
        when(fixtures.processedEventRepository.existsById(eventId)).thenReturn(true);

        fixtures.service.deleteUserData(event);

        verify(fixtures.mealContributionRepository, never()).deleteByUserId(event.userId());
    }

    private FitSyncDomainEvent<AccountDeletedPayload> event(UUID eventId, UUID userId) {
        return new FitSyncDomainEvent<>(
                eventId,
                "AccountDeleted",
                1,
                Instant.parse("2026-06-25T04:00:00Z"),
                "user-service",
                userId,
                new AccountDeletedPayload(Instant.parse("2026-06-25T04:00:00Z"), "USER_REQUESTED"));
    }

    private static final class Fixtures {
        private final AnalyticsProcessedEventRepository processedEventRepository =
                mock(AnalyticsProcessedEventRepository.class);
        private final AnalyticsMealContributionRepository mealContributionRepository =
                mock(AnalyticsMealContributionRepository.class);
        private final AnalyticsWorkoutContributionRepository workoutContributionRepository =
                mock(AnalyticsWorkoutContributionRepository.class);
        private final AnalyticsUserDailyBodyRepository dailyBodyRepository =
                mock(AnalyticsUserDailyBodyRepository.class);
        private final AnalyticsUserDailyNutritionRepository dailyNutritionRepository =
                mock(AnalyticsUserDailyNutritionRepository.class);
        private final AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository =
                mock(AnalyticsUserDailyWorkoutRepository.class);
        private final AnalyticsUserGoalProgressRepository goalProgressRepository =
                mock(AnalyticsUserGoalProgressRepository.class);
        private final AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository =
                mock(AnalyticsUserDashboardSnapshotRepository.class);
        private final AnalyticsUserStreakRollupRepository streakRollupRepository =
                mock(AnalyticsUserStreakRollupRepository.class);
        private final AnalyticsUserWorkoutRecordsRepository workoutRecordsRepository =
                mock(AnalyticsUserWorkoutRecordsRepository.class);
        private final AnalyticsUserEnergyProfileRepository energyProfileRepository =
                mock(AnalyticsUserEnergyProfileRepository.class);
        private final AnalyticsCacheInvalidationService cacheInvalidationService =
                mock(AnalyticsCacheInvalidationService.class);
        private final AccountDeletionProjectionService service = new AccountDeletionProjectionService(
                processedEventRepository,
                mealContributionRepository,
                workoutContributionRepository,
                dailyBodyRepository,
                dailyNutritionRepository,
                dailyWorkoutRepository,
                goalProgressRepository,
                dashboardSnapshotRepository,
                streakRollupRepository,
                workoutRecordsRepository,
                energyProfileRepository,
                cacheInvalidationService,
                Clock.fixed(Instant.parse("2026-06-25T04:00:00Z"), ZoneOffset.UTC));
    }
}
