package com.fitsync.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserRegisteredPayload;
import com.fitsync.user.messaging.UserRegisteredEventConsumer;
import com.fitsync.user.repository.UserAppPreferencesRepository;
import com.fitsync.user.repository.UserNotificationPreferencesRepository;
import com.fitsync.user.repository.UserNutritionPreferencesRepository;
import com.fitsync.user.repository.UserProfileRepository;
import com.fitsync.user.repository.UserTrainingPreferencesRepository;
import com.fitsync.user.security.CurrentUserPrincipal;
import com.fitsync.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class UserRegisteredEventConsumerTests {

    @Autowired
    private UserRegisteredEventConsumer consumer;

    @Autowired
    private UserService userService;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private UserAppPreferencesRepository userAppPreferencesRepository;

    @Autowired
    private UserTrainingPreferencesRepository userTrainingPreferencesRepository;

    @Autowired
    private UserNutritionPreferencesRepository userNutritionPreferencesRepository;

    @Autowired
    private UserNotificationPreferencesRepository userNotificationPreferencesRepository;

    @Test
    void userRegisteredCreatesProfileAndPreferencesScaffold() {
        UUID userId = UUID.randomUUID();
        consumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "UserRegistered",
                1,
                Instant.parse("2026-06-11T14:00:00Z"),
                "auth-service",
                userId,
                new UserRegisteredPayload(
                        "new-user@example.com",
                        "New User",
                        "USER",
                        Instant.parse("2026-06-11T14:00:00Z"))));

        assertThat(userProfileRepository.findById(userId))
                .isPresent()
                .get()
                .extracting(profile -> profile.getDisplayName())
                .isEqualTo("New User");

        assertThat(userAppPreferencesRepository.findById(userId)).isPresent();
        assertThat(userTrainingPreferencesRepository.findById(userId)).isPresent();
        assertThat(userNutritionPreferencesRepository.findById(userId)).isPresent();
        assertThat(userNotificationPreferencesRepository.findById(userId)).isPresent();
    }

    @Test
    void userRegisteredIsIdempotent() {
        UUID userId = UUID.randomUUID();
        FitSyncDomainEvent<UserRegisteredPayload> event = new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "UserRegistered",
                1,
                Instant.parse("2026-06-11T14:00:00Z"),
                "auth-service",
                userId,
                new UserRegisteredPayload(
                        "repeat@example.com",
                        "Repeat User",
                        "USER",
                        Instant.parse("2026-06-11T14:00:00Z")));

        consumer.consume(event);
        consumer.consume(event);

        assertThat(userProfileRepository.count()).isGreaterThanOrEqualTo(1L);
        assertThat(userProfileRepository.findById(userId)).isPresent();
        assertThat(userAppPreferencesRepository.findById(userId)).isPresent();
        assertThat(userTrainingPreferencesRepository.findById(userId)).isPresent();
        assertThat(userNutritionPreferencesRepository.findById(userId)).isPresent();
        assertThat(userNotificationPreferencesRepository.findById(userId)).isPresent();
    }

    @Test
    void registrationAndFirstReadStartAUserWithTheSameDefaults() {
        UUID registered = UUID.randomUUID();
        consumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "UserRegistered",
                1,
                Instant.parse("2026-06-11T14:00:00Z"),
                "auth-service",
                registered,
                new UserRegisteredPayload(
                        "registered@example.com",
                        "Same Defaults",
                        "USER",
                        Instant.parse("2026-06-11T14:00:00Z"))));
        UUID firstRead = UUID.randomUUID();

        var provisioned = userService.getCurrentUser(
                new CurrentUserPrincipal(registered, "registered@example.com", "Same Defaults", "USER"));
        var lazilyCreated = userService.getCurrentUser(
                new CurrentUserPrincipal(firstRead, "first-read@example.com", "Same Defaults", "USER"));

        assertThat(provisioned.preferences()).isEqualTo(lazilyCreated.preferences());
        assertThat(provisioned.profile()).usingRecursiveComparison()
                .ignoringFields("createdAt")
                .isEqualTo(lazilyCreated.profile());
    }
}
