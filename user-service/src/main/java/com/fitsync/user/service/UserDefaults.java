package com.fitsync.user.service;

import java.util.UUID;

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
import com.fitsync.user.entity.UserHealthReadinessEntity;
import com.fitsync.user.entity.UserNotificationPreferencesEntity;
import com.fitsync.user.entity.UserNutritionPreferencesEntity;
import com.fitsync.user.entity.UserProfileEntity;
import com.fitsync.user.entity.UserTrainingPreferencesEntity;

/**
 * The default profile and preference rows of a user who has not set anything yet. Rows are created either when
 * the UserRegistered event arrives or on the user's first read, whichever comes first, so both paths build them
 * here; otherwise the defaults a user starts with would depend on which path happened to run.
 */
final class UserDefaults {

    private UserDefaults() {
    }

    static UserProfileEntity profile(UUID userId, String displayName) {
        UserProfileEntity entity = new UserProfileEntity();
        entity.setUserId(userId);
        entity.setDisplayName(displayName);
        entity.setMembershipTier(MembershipTier.MEMBER);
        entity.setMembershipStatus(MembershipStatus.ACTIVE);
        entity.setOnboardingCompleted(false);
        return entity;
    }

    static UserAppPreferencesEntity appPreferences(UUID userId) {
        UserAppPreferencesEntity entity = new UserAppPreferencesEntity();
        entity.setUserId(userId);
        entity.setPreferredUnits(UnitSystem.METRIC);
        entity.setLanguage(LanguagePreference.EN);
        entity.setPrivacyLevel(PrivacyLevel.PRIVATE);
        entity.setDataSharingConsent(false);
        return entity;
    }

    static UserTrainingPreferencesEntity trainingPreferences(UUID userId) {
        UserTrainingPreferencesEntity entity = new UserTrainingPreferencesEntity();
        entity.setUserId(userId);
        entity.setFitnessLevel(FitnessLevel.BEGINNER);
        entity.setTrainingLocation(TrainingLocation.MIXED);
        entity.setIntensityPreference(IntensityPreference.MODERATE);
        return entity;
    }

    static UserNutritionPreferencesEntity nutritionPreferences(UUID userId) {
        UserNutritionPreferencesEntity entity = new UserNutritionPreferencesEntity();
        entity.setUserId(userId);
        entity.setDietaryPattern(DietaryPreference.NONE);
        entity.setCalorieTrackingMode(CalorieTrackingMode.FLEXIBLE);
        entity.setMacroPreference(MacroPreference.BALANCED);
        return entity;
    }

    static UserNotificationPreferencesEntity notificationPreferences(UUID userId) {
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
        return entity;
    }

    static UserHealthReadinessEntity healthReadiness(UUID userId) {
        UserHealthReadinessEntity entity = new UserHealthReadinessEntity();
        entity.setUserId(userId);
        return entity;
    }
}
