package com.fitsync.user.service;

import com.fitsync.contracts.events.FitSyncDomainEvent;
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

    public UserRegistrationProvisioningServiceImpl(UserProfileRepository userProfileRepository,
                                                   UserAppPreferencesRepository userAppPreferencesRepository,
                                                   UserTrainingPreferencesRepository userTrainingPreferencesRepository,
                                                   UserNutritionPreferencesRepository userNutritionPreferencesRepository,
                                                   UserNotificationPreferencesRepository userNotificationPreferencesRepository) {
        this.userProfileRepository = userProfileRepository;
        this.userAppPreferencesRepository = userAppPreferencesRepository;
        this.userTrainingPreferencesRepository = userTrainingPreferencesRepository;
        this.userNutritionPreferencesRepository = userNutritionPreferencesRepository;
        this.userNotificationPreferencesRepository = userNotificationPreferencesRepository;
    }

    @Override
    public void provisionFromRegistration(FitSyncDomainEvent<UserRegisteredPayload> event) {
        validateEvent(event);

        userProfileRepository.findById(event.userId()).orElseGet(() -> {
            UserProfileEntity entity = new UserProfileEntity();
            entity.setUserId(event.userId());
            entity.setDisplayName(event.payload().displayName());
            entity.setMembershipTier(MembershipTier.MEMBER);
            entity.setMembershipStatus(MembershipStatus.ACTIVE);
            entity.setOnboardingCompleted(false);
            return userProfileRepository.save(entity);
        });

        userAppPreferencesRepository.findById(event.userId()).orElseGet(() -> {
            UserAppPreferencesEntity entity = new UserAppPreferencesEntity();
            entity.setUserId(event.userId());
            entity.setPreferredUnits(UnitSystem.METRIC);
            entity.setLanguage(LanguagePreference.EN);
            entity.setPrivacyLevel(PrivacyLevel.PRIVATE);
            entity.setDataSharingConsent(false);
            return userAppPreferencesRepository.save(entity);
        });

        userTrainingPreferencesRepository.findById(event.userId()).orElseGet(() -> {
            UserTrainingPreferencesEntity entity = new UserTrainingPreferencesEntity();
            entity.setUserId(event.userId());
            entity.setFitnessLevel(FitnessLevel.BEGINNER);
            entity.setTrainingLocation(TrainingLocation.MIXED);
            entity.setIntensityPreference(IntensityPreference.MODERATE);
            return userTrainingPreferencesRepository.save(entity);
        });

        userNutritionPreferencesRepository.findById(event.userId()).orElseGet(() -> {
            UserNutritionPreferencesEntity entity = new UserNutritionPreferencesEntity();
            entity.setUserId(event.userId());
            entity.setDietaryPattern(DietaryPreference.NONE);
            entity.setCalorieTrackingMode(CalorieTrackingMode.FLEXIBLE);
            entity.setMacroPreference(MacroPreference.BALANCED);
            return userNutritionPreferencesRepository.save(entity);
        });

        userNotificationPreferencesRepository.findById(event.userId()).orElseGet(() -> {
            UserNotificationPreferencesEntity entity = new UserNotificationPreferencesEntity();
            entity.setUserId(event.userId());
            entity.setPushEnabled(true);
            entity.setEmailEnabled(false);
            entity.setWorkoutReminderEnabled(true);
            entity.setMealReminderEnabled(false);
            entity.setWeighInReminderEnabled(false);
            entity.setGoalProgressReminderEnabled(true);
            entity.setWeeklyProgressReportEnabled(false);
            entity.setWeeklyProgressReportChannel(NotificationChannel.EMAIL);
            entity.setMarketingEmailsEnabled(false);
            return userNotificationPreferencesRepository.save(entity);
        });
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
