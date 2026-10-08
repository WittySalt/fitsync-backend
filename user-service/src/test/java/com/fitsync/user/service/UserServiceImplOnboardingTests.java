package com.fitsync.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.media.MediaStorageService;
import com.fitsync.user.domain.GoalType;
import com.fitsync.user.dto.CompleteOnboardingRequest;
import com.fitsync.user.entity.BodyMetricEntryEntity;
import com.fitsync.user.entity.UserAppPreferencesEntity;
import com.fitsync.user.entity.UserGoalEntity;
import com.fitsync.user.entity.UserNotificationPreferencesEntity;
import com.fitsync.user.entity.UserNutritionPreferencesEntity;
import com.fitsync.user.entity.UserProfileEntity;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

class UserServiceImplOnboardingTests {

    // 20:00 UTC on 30 June is already 04:00 on 1 July in Singapore.
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-30T20:00:00Z"), ZoneOffset.UTC);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final UserProfileRepository profiles = mock(UserProfileRepository.class);
    private final UserAppPreferencesRepository app = mock(UserAppPreferencesRepository.class);
    private final UserTrainingPreferencesRepository training = mock(UserTrainingPreferencesRepository.class);
    private final UserNutritionPreferencesRepository nutrition = mock(UserNutritionPreferencesRepository.class);
    private final UserNotificationPreferencesRepository notifications =
            mock(UserNotificationPreferencesRepository.class);
    private final BodyMetricEntryRepository metrics = mock(BodyMetricEntryRepository.class);
    private final UserGoalRepository goals = mock(UserGoalRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<MediaStorageService> media = mock(ObjectProvider.class);

    private final UserServiceImpl service = new UserServiceImpl(
            mock(DomainEventPublisher.class), media, mock(AccountDataExportRequestRepository.class), profiles, app,
            training, nutrition, mock(UserHealthReadinessRepository.class), notifications, metrics, goals,
            mock(UserGuideStateRepository.class), mock(EnergyProfileService.class), clock);

    @Test
    void defaultTargetDateAndAgeAreReadInTheOnboardingTimezone() throws Exception {
        List<UserGoalEntity> saved = onboard("1996-07-01");

        // In Singapore it is 1 July: the target is three months from then and the 30th birthday has passed.
        assertThat(goal(saved, GoalType.WEIGHT).getTargetDate()).isEqualTo(LocalDate.parse("2026-10-01"));
        // BMR = 10*72 + 6.25*168 - 5*30 - 161 = 1459; * 1.55 = 2261.45; - 400 to lose weight = 1861.
        assertThat(goal(saved, GoalType.CALORIES).getTargetValue()).isEqualByComparingTo("1861");
    }

    @Test
    void calorieEstimateUsesTheExactAgeLikeTheEnergyProfile() throws Exception {
        List<UserGoalEntity> saved = onboard("1996-12-31");

        // 29 until the end of December, not 2026 - 1996 = 30.
        // BMR = 10*72 + 6.25*168 - 5*29 - 161 = 1464; * 1.55 = 2269.2; - 400 = 1869.
        assertThat(goal(saved, GoalType.CALORIES).getTargetValue()).isEqualByComparingTo("1869");
    }

    private List<UserGoalEntity> onboard(String birthDate) throws Exception {
        when(profiles.save(any(UserProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(app.save(any(UserAppPreferencesEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(training.save(any(UserTrainingPreferencesEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(nutrition.save(any(UserNutritionPreferencesEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notifications.save(any(UserNotificationPreferencesEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(metrics.save(any(BodyMetricEntryEntity.class))).thenAnswer(invocation -> {
            BodyMetricEntryEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "metricId", UUID.randomUUID());
            return entity;
        });
        when(goals.save(any(UserGoalEntity.class))).thenAnswer(invocation -> {
            UserGoalEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "goalId", UUID.randomUUID());
            return entity;
        });

        CompleteOnboardingRequest request = objectMapper.readValue("""
                {
                  "displayName": "Zone User",
                  "birthDate": "%s",
                  "gender": "FEMALE",
                  "heightCm": 168.0,
                  "activityLevel": "MODERATELY_ACTIVE",
                  "timezone": "Asia/Singapore",
                  "preferredUnits": "100001",
                  "currentWeightKg": 72.0,
                  "targetWeightKg": 68.0,
                  "fitnessLevel": "200001",
                  "trainingLocation": "210004",
                  "preferredDurationMinutes": 45,
                  "intensityPreference": "220002",
                  "dietaryPattern": "300001",
                  "mealFrequency": 4,
                  "calorieTrackingMode": "310002",
                  "macroPreference": "320001"
                }
                """.formatted(birthDate), CompleteOnboardingRequest.class);

        service.completeOnboarding(
                new CurrentUserPrincipal(UUID.randomUUID(), "zone@test.dev", "Zone User", "USER"), request);

        ArgumentCaptor<UserGoalEntity> captor = ArgumentCaptor.forClass(UserGoalEntity.class);
        verify(goals, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private static UserGoalEntity goal(List<UserGoalEntity> saved, GoalType type) {
        return saved.stream().filter(goal -> goal.getGoalType() == type).findFirst().orElseThrow();
    }
}
