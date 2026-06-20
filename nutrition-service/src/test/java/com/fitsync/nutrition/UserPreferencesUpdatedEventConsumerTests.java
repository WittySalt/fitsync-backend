package com.fitsync.nutrition;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserPreferencesUpdatedPayload;
import com.fitsync.nutrition.messaging.UserPreferencesUpdatedEventConsumer;
import com.fitsync.nutrition.repository.NutritionPreferenceProjectionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class UserPreferencesUpdatedEventConsumerTests {

    @Autowired
    private UserPreferencesUpdatedEventConsumer consumer;

    @Autowired
    private NutritionPreferenceProjectionRepository repository;

    @Test
    void userPreferencesUpdatedRefreshesNutritionPreferenceProjection() {
        UUID userId = UUID.randomUUID();

        consumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "UserPreferencesUpdated",
                1,
                Instant.parse("2026-06-12T00:20:00Z"),
                "user-service",
                userId,
                new UserPreferencesUpdatedPayload("100001", 4, "300003", true, List.of(), List.of())));

        assertThat(repository.findById(userId))
                .isPresent()
                .get()
                .satisfies(projection -> {
                    assertThat(projection.getPreferredUnitsCode()).isEqualTo("100001");
                    assertThat(projection.getDietaryPreferenceCode()).isEqualTo("300003");
                    assertThat(projection.getWorkoutDaysPerWeek()).isEqualTo(4);
                    assertThat(projection.isNotificationsEnabled()).isTrue();
                });
    }
}
