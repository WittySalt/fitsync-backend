package com.fitsync.user.service;

import java.util.UUID;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.NotificationPreferencesUpdatedPayload;
import com.fitsync.contracts.events.user.UserRegisteredPayload;
import com.fitsync.user.domain.CalorieTrackingMode;
import com.fitsync.user.domain.DietaryPreference;
import com.fitsync.user.domain.FitnessLevel;
import com.fitsync.user.domain.IntensityPreference;
import com.fitsync.user.domain.LanguagePreference;
import com.fitsync.user.domain.MacroPreference;
import com.fitsync.user.domain.MembershipStatus;
import com.fitsync.user.domain.MembershipTier;
import com.fitsync.user.domain.NotificationChannel;
import com.fitsync.user.domain.PrivacyLevel;
import com.fitsync.user.domain.TrainingLocation;
import com.fitsync.user.domain.UnitSystem;
import com.fitsync.user.entity.UserAppPreferencesEntity;
import com.fitsync.user.entity.UserNotificationPreferencesEntity;
import com.fitsync.user.entity.UserNutritionPreferencesEntity;
import com.fitsync.user.entity.UserProfileEntity;
import com.fitsync.user.entity.UserTrainingPreferencesEntity;
import com.fitsync.user.repository.UserAppPreferencesRepository;
import com.fitsync.user.repository.UserNotificationPreferencesRepository;
import com.fitsync.user.repository.UserNutritionPreferencesRepository;
import com.fitsync.user.repository.UserProfileRepository;
import com.fitsync.user.repository.UserTrainingPreferencesRepository;
import org.springframework.stereotype.Service;

@Service
public class UserRegistrationProvisioningServiceImpl implements UserRegistrationProvisioningService {

    private final UserProfileRepository userProfileRepository;
    private final UserAppPreferencesRepository userAppPreferencesRepository;
    private final UserTrainingPreferencesRepository userTrainingPreferencesRepository;
    private final UserNutritionPreferencesRepository userNutritionPreferencesRepository;
    private final UserNotificationPreferencesRepository userNotificationPreferencesRepository;
    private final NotificationPreferencesEventService notificationPreferencesEventService;

    public UserRegistrationProvisioningServiceImpl(UserProfileRepository userProfileRepository,
                                                   UserAppPreferencesRepository userAppPreferencesRepository,
                                                   UserTrainingPreferencesRepository userTrainingPreferencesRepository,
                                                   UserNutritionPreferencesRepository userNutritionPreferencesRepository,
                                                   UserNotificationPreferencesRepository userNotificationPreferencesRepository,
                                                   NotificationPreferencesEventService notificationPreferencesEventService) {
        this.userProfileRepository = userProfileRepository;
        this.userAppPreferencesRepository = userAppPreferencesRepository;
        this.userTrainingPreferencesRepository = userTrainingPreferencesRepository;
        this.userNutritionPreferencesRepository = userNutritionPreferencesRepository;
        this.userNotificationPreferencesRepository = userNotificationPreferencesRepository;
        this.notificationPreferencesEventService = notificationPreferencesEventService;
    }

    @Override
    public void provisionFromRegistration(FitSyncDomainEvent<UserRegisteredPayload> event) {
        validateEvent(event);

        UUID userId = event.userId();
        NotificationPreferencesUpdatedPayload notificationsBefore = notificationPreferencesEventService.snapshot(userId);

        if (!userProfileRepository.existsById(userId)) {
            UserProfileEntity entity = new UserProfileEntity();
            entity.setUserId(userId);
            entity.setDisplayName(event.payload().displayName());
            entity.setMembershipTier(MembershipTier.MEMBER);
            entity.setMembershipStatus(MembershipStatus.ACTIVE);
            entity.setOnboardingCompleted(false);
            userProfileRepository.save(entity);
        }

        if (!userAppPreferencesRepository.existsById(userId)) {
            UserAppPreferencesEntity entity = new UserAppPreferencesEntity();
            entity.setUserId(userId);
            entity.setPreferredUnits(UnitSystem.METRIC);
            entity.setLanguage(LanguagePreference.EN);
            entity.setPrivacyLevel(PrivacyLevel.PRIVATE);
            entity.setDataSharingConsent(false);
            userAppPreferencesRepository.save(entity);
        }

        if (!userTrainingPreferencesRepository.existsById(userId)) {
            UserTrainingPreferencesEntity entity = new UserTrainingPreferencesEntity();
            entity.setUserId(userId);
            entity.setFitnessLevel(FitnessLevel.BEGINNER);
            entity.setTrainingLocation(TrainingLocation.MIXED);
            entity.setIntensityPreference(IntensityPreference.MODERATE);
            userTrainingPreferencesRepository.save(entity);
        }

        if (!userNutritionPreferencesRepository.existsById(userId)) {
            UserNutritionPreferencesEntity entity = new UserNutritionPreferencesEntity();
            entity.setUserId(userId);
            entity.setDietaryPattern(DietaryPreference.NONE);
            entity.setCalorieTrackingMode(CalorieTrackingMode.FLEXIBLE);
            entity.setMacroPreference(MacroPreference.BALANCED);
            userNutritionPreferencesRepository.save(entity);
        }

        if (!userNotificationPreferencesRepository.existsById(userId)) {
            UserNotificationPreferencesEntity entity = new UserNotificationPreferencesEntity();
            entity.setUserId(userId);
            entity.setPushEnabled(true);
            entity.setEmailEnabled(false);
            entity.setWorkoutReminderEnabled(true);
            entity.setMealReminderEnabled(false);
            entity.setWeighInReminderEnabled(false);
            entity.setGoalProgressReminderEnabled(true);
            entity.setWeeklyProgressReportEnabled(false);
            entity.setWeeklyProgressReportChannel(NotificationChannel.EMAIL);
            entity.setMarketingEmailsEnabled(false);
            userNotificationPreferencesRepository.save(entity);
        }

        // A replayed UserRegistered finds every row in place and so publishes nothing.
        notificationPreferencesEventService.publishIfChanged(userId, notificationsBefore);
    }

    private void validateEvent(FitSyncDomainEvent<UserRegisteredPayload> event) {
        if (event == null || event.userId() == null || event.payload() == null) {
            throw new IllegalArgumentException("UserRegistered event payload is required");
        }
        if (event.payload().email() == null || event.payload().email().isBlank()) {
            throw new IllegalArgumentException("UserRegistered event email is required");
        }
        if (event.payload().displayName() == null || event.payload().displayName().isBlank()) {
            throw new IllegalArgumentException("UserRegistered event displayName is required");
        }
    }
}
