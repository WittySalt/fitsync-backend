package com.fitsync.user.service;

import java.util.UUID;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.NotificationPreferencesUpdatedPayload;
import com.fitsync.contracts.events.user.UserRegisteredPayload;
import com.fitsync.user.repository.UserAppPreferencesRepository;
import com.fitsync.user.repository.UserNotificationPreferencesRepository;
import com.fitsync.user.repository.UserNutritionPreferencesRepository;
import com.fitsync.user.repository.UserProfileRepository;
import com.fitsync.user.repository.UserTrainingPreferencesRepository;
import org.springframework.stereotype.Service;

/**
 * Creates the default profile and preference rows for a newly registered user. Each row is only created when
 * missing, so a redelivered event, or a user whose rows were already created on first read, is left as is. The
 * defaults come from {@link UserDefaults}, the same ones the first read uses.
 */
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
            userProfileRepository.save(UserDefaults.profile(userId, event.payload().displayName()));
        }
        if (!userAppPreferencesRepository.existsById(userId)) {
            userAppPreferencesRepository.save(UserDefaults.appPreferences(userId));
        }
        if (!userTrainingPreferencesRepository.existsById(userId)) {
            userTrainingPreferencesRepository.save(UserDefaults.trainingPreferences(userId));
        }
        if (!userNutritionPreferencesRepository.existsById(userId)) {
            userNutritionPreferencesRepository.save(UserDefaults.nutritionPreferences(userId));
        }
        if (!userNotificationPreferencesRepository.existsById(userId)) {
            userNotificationPreferencesRepository.save(UserDefaults.notificationPreferences(userId));
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
