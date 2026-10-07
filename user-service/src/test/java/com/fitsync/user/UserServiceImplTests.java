package com.fitsync.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.AccountDeletedPayload;
import com.fitsync.contracts.events.user.GoalUpdatedPayload;
import com.fitsync.media.MediaStorageService;
import com.fitsync.user.domain.CalorieTrackingMode;
import com.fitsync.user.domain.DietaryPreference;
import com.fitsync.user.domain.FitnessLevel;
import com.fitsync.user.domain.IntensityPreference;
import com.fitsync.user.domain.LanguagePreference;
import com.fitsync.user.domain.MacroPreference;
import com.fitsync.user.domain.NotificationChannel;
import com.fitsync.user.domain.PrivacyLevel;
import com.fitsync.user.domain.TrainingEquipment;
import com.fitsync.user.domain.TrainingFocusArea;
import com.fitsync.user.domain.TrainingLocation;
import com.fitsync.user.domain.UnitSystem;
import com.fitsync.user.domain.GoalStatus;
import com.fitsync.user.domain.GoalType;
import com.fitsync.user.dto.HealthReadinessView;
import com.fitsync.user.dto.ProgressInsightResponse;
import com.fitsync.user.dto.UpsertAppPreferencesRequest;
import com.fitsync.user.dto.UpsertHealthReadinessRequest;
import com.fitsync.user.dto.UpsertNotificationPreferencesRequest;
import com.fitsync.user.dto.UpsertNutritionPreferencesRequest;
import com.fitsync.user.dto.UpsertTrainingPreferencesRequest;
import com.fitsync.user.dto.UpsertUserGoalRequest;
import com.fitsync.user.dto.UpsertUserPreferencesRequest;
import com.fitsync.user.entity.BodyMetricEntryEntity;
import com.fitsync.user.entity.UserAppPreferencesEntity;
import com.fitsync.user.entity.UserGoalEntity;
import com.fitsync.user.entity.UserHealthReadinessEntity;
import com.fitsync.user.entity.UserNotificationPreferencesEntity;
import com.fitsync.user.entity.UserNutritionPreferencesEntity;
import com.fitsync.user.entity.UserTrainingPreferencesEntity;
import com.fitsync.user.messaging.DomainEventPublisher;
import com.fitsync.user.repository.AccountDataExportRequestRepository;
import com.fitsync.user.repository.BodyMetricEntryRepository;
import com.fitsync.user.repository.UserAppPreferencesRepository;
import com.fitsync.user.repository.UserGoalRepository;
import com.fitsync.user.repository.UserGuideStateRepository;
import com.fitsync.user.repository.UserHealthReadinessRepository;
import com.fitsync.user.repository.UserNotificationPreferencesRepository;
import com.fitsync.user.repository.UserNutritionPreferencesRepository;
import com.fitsync.user.repository.UserProfileRepository;
import com.fitsync.user.repository.UserTrainingPreferencesRepository;
import com.fitsync.user.security.CurrentUserPrincipal;
import com.fitsync.user.service.EnergyProfileService;
import com.fitsync.user.service.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

class UserServiceImplTests {

    @Test
    void upsertPreferencesRejectsNoneCombinedWithOtherEquipment() {
        Fixtures fixtures = new Fixtures();
        UUID userId = UUID.randomUUID();
        CurrentUserPrincipal principal = new CurrentUserPrincipal(userId, "prefs@test.dev", "Prefs", "USER");
        when(fixtures.userAppPreferencesRepository.findById(userId)).thenReturn(Optional.of(new UserAppPreferencesEntity()));
        when(fixtures.userTrainingPreferencesRepository.findById(userId))
                .thenReturn(Optional.of(new UserTrainingPreferencesEntity()));

        UpsertUserPreferencesRequest request = new UpsertUserPreferencesRequest(
                new UpsertAppPreferencesRequest(
                        UnitSystem.METRIC,
                        LanguagePreference.EN,
                        "Asia/Singapore",
                        PrivacyLevel.PRIVATE,
                        false),
                new UpsertTrainingPreferencesRequest(
                        FitnessLevel.BEGINNER,
                        TrainingLocation.MIXED,
                        3,
                        45,
                        null,
                        IntensityPreference.MODERATE,
                        java.util.List.of(TrainingEquipment.NONE, TrainingEquipment.DUMBBELLS),
                        java.util.List.of(TrainingFocusArea.FULL_BODY)),
                new UpsertNutritionPreferencesRequest(
                        DietaryPreference.NONE,
                        3,
                        CalorieTrackingMode.FLEXIBLE,
                        MacroPreference.BALANCED,
                        2500),
                new UpsertNotificationPreferencesRequest(
                        true,
                        false,
                        true,
                        false,
                        false,
                        true,
                        false,
                        NotificationChannel.EMAIL,
                        false,
                        null,
                        null));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> fixtures.service.upsertPreferences(principal, request));

        assertEquals("equipmentInventory cannot combine NONE with other equipment", error.getMessage());
    }

    @Test
    void upsertHealthReadinessReturnsDistinctInjuryAreas() {
        Fixtures fixtures = new Fixtures();
        UUID userId = UUID.randomUUID();
        CurrentUserPrincipal principal = new CurrentUserPrincipal(userId, "health@test.dev", "Health", "USER");
        when(fixtures.userHealthReadinessRepository.findById(userId)).thenReturn(Optional.empty());
        when(fixtures.userHealthReadinessRepository.save(any(UserHealthReadinessEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HealthReadinessView view = fixtures.service.upsertHealthReadiness(
                principal,
                new UpsertHealthReadinessRequest(
                        true,
                        false,
                        true,
                        false,
                        false,
                        false,
                        java.util.List.of("LEFT_KNEE", "LOWER_BACK", "LEFT_KNEE"),
                        Instant.parse("2026-06-20T09:15:00Z")));

        assertEquals(java.util.List.of("LEFT_KNEE", "LOWER_BACK"), view.injuryAreas());
        assertEquals(Instant.parse("2026-06-20T09:15:00Z"), view.acknowledgedDisclaimerAt());
    }

    @Test
    void republishActiveGoalsPublishesGoalUpdatedForEachActiveGoal() {
        Fixtures fixtures = new Fixtures();
        UUID userId = UUID.randomUUID();
        UserGoalEntity calories = goal(userId, GoalType.CALORIES, GoalStatus.ACTIVE, "2100.00", "kcal");
        UserGoalEntity protein = goal(userId, GoalType.PROTEIN, GoalStatus.ACTIVE, "140.00", "g");
        when(fixtures.userGoalRepository.findByStatusOrderByUserIdAscGoalTypeAsc(GoalStatus.ACTIVE))
                .thenReturn(java.util.List.of(calories, protein));

        int publishedCount = fixtures.service.republishActiveGoals();

        assertEquals(2, publishedCount);
        ArgumentCaptor<FitSyncDomainEvent<?>> eventCaptor = ArgumentCaptor.forClass(FitSyncDomainEvent.class);
        verify(fixtures.domainEventPublisher, times(2))
                .publish(org.mockito.ArgumentMatchers.eq("progress-events"),
                        org.mockito.ArgumentMatchers.eq("UserGoal"),
                        org.mockito.ArgumentMatchers.anyString(),
                        eventCaptor.capture());
        java.util.List<String> goalTypes = eventCaptor.getAllValues().stream()
                .map(event -> ((GoalUpdatedPayload) event.payload()).goalType())
                .toList();
        assertEquals(java.util.List.of("CALORIES", "PROTEIN"), goalTypes);
        eventCaptor.getAllValues().forEach(event ->
                assertEquals("ACTIVE", ((GoalUpdatedPayload) event.payload()).status()));
    }

    @Test
    void upsertGoalPublishesNonActiveGoalUpdatedEvent() {
        Fixtures fixtures = new Fixtures();
        UUID userId = UUID.randomUUID();
        CurrentUserPrincipal principal = new CurrentUserPrincipal(userId, "goals@test.dev", "Goal User", "USER");
        UserGoalEntity existing = goal(userId, GoalType.PROTEIN, GoalStatus.ACTIVE, "155.00", "g");
        when(fixtures.userGoalRepository.findByUserIdAndGoalType(userId, GoalType.PROTEIN))
                .thenReturn(Optional.of(existing));
        when(fixtures.userGoalRepository.save(any(UserGoalEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        fixtures.service.upsertGoal(
                principal,
                GoalType.PROTEIN,
                new UpsertUserGoalRequest(
                        new java.math.BigDecimal("155.00"),
                        "g",
                        java.time.LocalDate.parse("2026-08-31"),
                        GoalStatus.CANCELLED,
                        "Archive protein target"));

        ArgumentCaptor<FitSyncDomainEvent<?>> eventCaptor = ArgumentCaptor.forClass(FitSyncDomainEvent.class);
        verify(fixtures.domainEventPublisher)
                .publish(org.mockito.ArgumentMatchers.eq("progress-events"),
                        org.mockito.ArgumentMatchers.eq("UserGoal"),
                        org.mockito.ArgumentMatchers.eq(existing.getGoalId().toString()),
                        eventCaptor.capture());
        GoalUpdatedPayload payload = (GoalUpdatedPayload) eventCaptor.getValue().payload();
        assertEquals("PROTEIN", payload.goalType());
        assertEquals("CANCELLED", payload.status());
    }

    @Test
    void deleteCurrentUserClearsLocalAccountDataAndPublishesDeletionEvent() {
        Fixtures fixtures = new Fixtures();
        UUID userId = UUID.randomUUID();
        CurrentUserPrincipal principal = new CurrentUserPrincipal(userId, "delete@test.dev", "Delete Me", "USER");

        fixtures.service.deleteCurrentUser(principal);

        verify(fixtures.accountDataExportRequestRepository).deleteByUserId(userId);
        verify(fixtures.bodyMetricEntryRepository).deleteByUserId(userId);
        verify(fixtures.userGoalRepository).deleteByUserId(userId);
        verify(fixtures.userHealthReadinessRepository).deleteById(userId);
        verify(fixtures.userNotificationPreferencesRepository).deleteById(userId);
        verify(fixtures.userNutritionPreferencesRepository).deleteById(userId);
        verify(fixtures.userTrainingPreferencesRepository).deleteById(userId);
        verify(fixtures.userAppPreferencesRepository).deleteById(userId);
        verify(fixtures.userProfileRepository).deleteById(userId);

        ArgumentCaptor<FitSyncDomainEvent<?>> eventCaptor = ArgumentCaptor.forClass(FitSyncDomainEvent.class);
        verify(fixtures.domainEventPublisher)
                .publish(org.mockito.ArgumentMatchers.eq("user-events"),
                        org.mockito.ArgumentMatchers.eq("UserAccount"),
                        org.mockito.ArgumentMatchers.eq(userId.toString()),
                        eventCaptor.capture());
        FitSyncDomainEvent<?> event = eventCaptor.getValue();
        assertEquals("AccountDeleted", event.eventType());
        assertEquals(userId, event.userId());
        AccountDeletedPayload payload = (AccountDeletedPayload) event.payload();
        assertEquals("USER_REQUESTED", payload.reason());
        assertEquals(event.occurredAt(), payload.deletedAt());
    }

    @Test
    void progressInsightWithoutMeasurementsOrGoalIsEmpty() {
        Fixtures fixtures = new Fixtures();
        UUID userId = UUID.randomUUID();
        CurrentUserPrincipal principal = new CurrentUserPrincipal(userId, "none@test.dev", "None", "USER");
        when(fixtures.bodyMetricEntryRepository.findTrendSource(any(), any(), any())).thenReturn(List.of());
        when(fixtures.bodyMetricEntryRepository
                .findTopByUserIdAndWeightKgIsNotNullAndMeasuredAtLessThanOrderByMeasuredAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(fixtures.userGoalRepository.findByUserIdOrderByGoalTypeAsc(userId)).thenReturn(List.of());

        ProgressInsightResponse response = fixtures.service.getProgressInsight(principal, ZoneId.of("UTC"));

        assertEquals("UTC", response.timezone());
        assertNull(response.weightKg());
        assertEquals(0, response.entries());
        assertNull(response.goalWeightKg());
        assertNull(response.goalDate());
        assertNull(response.projectedDate());
        assertEquals(List.of(), response.series());
    }

    @Test
    void progressInsightWithoutAZoneReadsTheProfileZoneThenTheAppZoneThenUtc() {
        Fixtures fixtures = new Fixtures();
        UUID userId = UUID.randomUUID();
        CurrentUserPrincipal principal = new CurrentUserPrincipal(userId, "zone@test.dev", "Zone", "USER");
        emptyProgressSources(fixtures, userId);

        // Nothing stored.
        assertEquals("UTC", fixtures.service.getProgressInsight(principal, null).timezone());

        // The app preferences zone is used when the profile has none.
        UserAppPreferencesEntity app = new UserAppPreferencesEntity();
        app.setTimezone("America/Los_Angeles");
        when(fixtures.userAppPreferencesRepository.findById(userId)).thenReturn(Optional.of(app));
        assertEquals("America/Los_Angeles", fixtures.service.getProgressInsight(principal, null).timezone());

        // The profile zone wins over the app preferences zone.
        com.fitsync.user.entity.UserProfileEntity profile = new com.fitsync.user.entity.UserProfileEntity();
        profile.setTimezone("Asia/Singapore");
        when(fixtures.userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        assertEquals("Asia/Singapore", fixtures.service.getProgressInsight(principal, null).timezone());

        // A zone the request names still wins over both.
        assertEquals("Europe/London",
                fixtures.service.getProgressInsight(principal, ZoneId.of("Europe/London")).timezone());

        // A stored zone that cannot be read is skipped.
        profile.setTimezone("Not/AZone");
        app.setTimezone(" ");
        assertEquals("UTC", fixtures.service.getProgressInsight(principal, null).timezone());
    }

    private void emptyProgressSources(Fixtures fixtures, UUID userId) {
        when(fixtures.bodyMetricEntryRepository.findTrendSource(any(), any(), any())).thenReturn(List.of());
        when(fixtures.bodyMetricEntryRepository
                .findTopByUserIdAndWeightKgIsNotNullAndMeasuredAtLessThanOrderByMeasuredAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(fixtures.userGoalRepository.findByUserIdOrderByGoalTypeAsc(userId)).thenReturn(List.of());
        when(fixtures.userProfileRepository.findById(userId)).thenReturn(Optional.empty());
        when(fixtures.userAppPreferencesRepository.findById(userId)).thenReturn(Optional.empty());
    }

    @Test
    void progressInsightIgnoresArchivedWeightGoalAndTakesTheNewestActiveOne() {
        Fixtures fixtures = new Fixtures();
        UUID userId = UUID.randomUUID();
        CurrentUserPrincipal principal = new CurrentUserPrincipal(userId, "goal@test.dev", "Goal", "USER");
        UserGoalEntity archived = goal(userId, GoalType.WEIGHT, GoalStatus.ARCHIVED, "60.00", "kg");
        UserGoalEntity older = goal(userId, GoalType.WEIGHT, GoalStatus.ACTIVE, "68.00", "kg");
        UserGoalEntity newer = goal(userId, GoalType.WEIGHT, GoalStatus.ACTIVE, "66.50", "kg");
        UserGoalEntity calories = goal(userId, GoalType.CALORIES, GoalStatus.ACTIVE, "2000.00", "kcal");
        ReflectionTestUtils.setField(archived, "dateTimeUpdated", Instant.parse("2026-09-29T00:00:00Z"));
        ReflectionTestUtils.setField(older, "dateTimeUpdated", Instant.parse("2026-09-01T00:00:00Z"));
        ReflectionTestUtils.setField(newer, "dateTimeUpdated", Instant.parse("2026-09-20T00:00:00Z"));
        ReflectionTestUtils.setField(calories, "dateTimeUpdated", Instant.parse("2026-09-28T00:00:00Z"));
        when(fixtures.userGoalRepository.findByUserIdOrderByGoalTypeAsc(userId))
                .thenReturn(List.of(archived, older, newer, calories));
        when(fixtures.bodyMetricEntryRepository.findTrendSource(any(), any(), any())).thenReturn(List.of());
        when(fixtures.bodyMetricEntryRepository
                .findTopByUserIdAndWeightKgIsNotNullAndMeasuredAtLessThanOrderByMeasuredAtDesc(any(), any()))
                .thenReturn(Optional.empty());

        ProgressInsightResponse response = fixtures.service.getProgressInsight(principal, ZoneId.of("UTC"));

        assertEquals(new java.math.BigDecimal("66.50"), response.goalWeightKg());
        assertEquals(java.time.LocalDate.parse("2026-08-31"), response.goalDate());
    }

    @Test
    void progressInsightMapsMeasurementsAndProjectsTowardTheGoal() {
        Fixtures fixtures = new Fixtures();
        UUID userId = UUID.randomUUID();
        CurrentUserPrincipal principal = new CurrentUserPrincipal(userId, "series@test.dev", "Series", "USER");
        Instant now = Instant.now();
        BodyMetricEntryEntity first = weight(userId, now.minus(15, ChronoUnit.DAYS), "74.0");
        BodyMetricEntryEntity noWeight = weight(userId, now.minus(10, ChronoUnit.DAYS), null);
        BodyMetricEntryEntity last = weight(userId, now.minus(1, ChronoUnit.DAYS), "72.0");
        when(fixtures.bodyMetricEntryRepository.findTrendSource(any(), any(), any()))
                .thenReturn(List.of(first, noWeight, last));
        when(fixtures.bodyMetricEntryRepository
                .findTopByUserIdAndWeightKgIsNotNullAndMeasuredAtLessThanOrderByMeasuredAtDesc(any(), any()))
                .thenReturn(Optional.of(last));
        UserGoalEntity goal = goal(userId, GoalType.WEIGHT, GoalStatus.ACTIVE, "70.00", "kg");
        when(fixtures.userGoalRepository.findByUserIdOrderByGoalTypeAsc(userId)).thenReturn(List.of(goal));

        ProgressInsightResponse response = fixtures.service.getProgressInsight(principal, ZoneId.of("UTC"));

        assertEquals(2, response.entries());
        assertEquals(new java.math.BigDecimal("72.0"), response.weightKg());
        assertEquals(new java.math.BigDecimal("-1.00"), response.weeklyRateKg());
        assertEquals(response.measuredOn().plusDays(14), response.projectedDate());
    }

    private static BodyMetricEntryEntity weight(UUID userId, Instant measuredAt, String weightKg) {
        BodyMetricEntryEntity entity = new BodyMetricEntryEntity();
        entity.setUserId(userId);
        entity.setMeasuredAt(measuredAt);
        entity.setWeightKg(weightKg == null ? null : new java.math.BigDecimal(weightKg));
        return entity;
    }

    private static UserGoalEntity goal(UUID userId,
                                       GoalType goalType,
                                       GoalStatus status,
                                       String targetValue,
                                       String targetUnit) {
        UserGoalEntity entity = new UserGoalEntity();
        ReflectionTestUtils.setField(entity, "goalId", UUID.randomUUID());
        entity.setUserId(userId);
        entity.setGoalType(goalType);
        entity.setTargetValue(new java.math.BigDecimal(targetValue));
        entity.setTargetUnit(targetUnit);
        entity.setTargetDate(java.time.LocalDate.parse("2026-08-31"));
        entity.setStatus(status);
        return entity;
    }

    private static final class Fixtures {
        private final DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        private final MediaStorageService mediaStorageService = mock(MediaStorageService.class);
        @SuppressWarnings("unchecked")
        private final ObjectProvider<MediaStorageService> mediaStorageServiceProvider = mock(ObjectProvider.class);
        private final AccountDataExportRequestRepository accountDataExportRequestRepository =
                mock(AccountDataExportRequestRepository.class);
        private final UserProfileRepository userProfileRepository = mock(UserProfileRepository.class);
        private final UserAppPreferencesRepository userAppPreferencesRepository =
                mock(UserAppPreferencesRepository.class);
        private final UserTrainingPreferencesRepository userTrainingPreferencesRepository =
                mock(UserTrainingPreferencesRepository.class);
        private final UserNutritionPreferencesRepository userNutritionPreferencesRepository =
                mock(UserNutritionPreferencesRepository.class);
        private final UserHealthReadinessRepository userHealthReadinessRepository =
                mock(UserHealthReadinessRepository.class);
        private final UserNotificationPreferencesRepository userNotificationPreferencesRepository =
                mock(UserNotificationPreferencesRepository.class);
        private final BodyMetricEntryRepository bodyMetricEntryRepository = mock(BodyMetricEntryRepository.class);
        private final UserGoalRepository userGoalRepository = mock(UserGoalRepository.class);
        private final UserGuideStateRepository userGuideStateRepository = mock(UserGuideStateRepository.class);
        private final EnergyProfileService energyProfileService = mock(EnergyProfileService.class);

        private Fixtures() {
            when(mediaStorageServiceProvider.getObject()).thenReturn(mediaStorageService);
        }

        private final UserServiceImpl service = new UserServiceImpl(
                domainEventPublisher,
                mediaStorageServiceProvider,
                accountDataExportRequestRepository,
                userProfileRepository,
                userAppPreferencesRepository,
                userTrainingPreferencesRepository,
                userNutritionPreferencesRepository,
                userHealthReadinessRepository,
                userNotificationPreferencesRepository,
                bodyMetricEntryRepository,
                userGoalRepository,
                userGuideStateRepository,
                energyProfileService);
    }
}
