package com.fitsync.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

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
import com.fitsync.user.dto.HealthReadinessView;
import com.fitsync.user.dto.UpsertAppPreferencesRequest;
import com.fitsync.user.dto.UpsertHealthReadinessRequest;
import com.fitsync.user.dto.UpsertNotificationPreferencesRequest;
import com.fitsync.user.dto.UpsertNutritionPreferencesRequest;
import com.fitsync.user.dto.UpsertTrainingPreferencesRequest;
import com.fitsync.user.dto.UpsertUserPreferencesRequest;
import com.fitsync.user.entity.UserAppPreferencesEntity;
import com.fitsync.user.entity.UserHealthReadinessEntity;
import com.fitsync.user.entity.UserNotificationPreferencesEntity;
import com.fitsync.user.entity.UserNutritionPreferencesEntity;
import com.fitsync.user.entity.UserTrainingPreferencesEntity;
import com.fitsync.user.messaging.DomainEventPublisher;
import com.fitsync.user.repository.AccountDataExportRequestRepository;
import com.fitsync.user.repository.BodyMetricEntryRepository;
import com.fitsync.user.repository.UserAppPreferencesRepository;
import com.fitsync.user.repository.UserGoalRepository;
import com.fitsync.user.repository.UserHealthReadinessRepository;
import com.fitsync.user.repository.UserNotificationPreferencesRepository;
import com.fitsync.user.repository.UserNutritionPreferencesRepository;
import com.fitsync.user.repository.UserProfileRepository;
import com.fitsync.user.repository.UserTrainingPreferencesRepository;
import com.fitsync.user.security.CurrentUserPrincipal;
import com.fitsync.user.service.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

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
                userGoalRepository);
    }
}
