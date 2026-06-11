package com.fitsync.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserRegisteredPayload;
import com.fitsync.user.repository.UserAppPreferencesRepository;
import com.fitsync.user.repository.UserNotificationPreferencesRepository;
import com.fitsync.user.repository.UserNutritionPreferencesRepository;
import com.fitsync.user.repository.UserProfileRepository;
import com.fitsync.user.repository.UserTrainingPreferencesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}",
        "fitsync.kafka.enabled=true",
        "fitsync.kafka.topics.user-events=fitsync-user-events-it",
        "fitsync.kafka.consumer-groups.user-service=user-service-it",
        "fitsync.kafka.listeners.user-registered.enabled=true"
})
@ActiveProfiles("test")
@DirtiesContext
@EnabledIfEnvironmentVariable(named = "KAFKA_INTEGRATION_TESTS", matches = "true")
class UserRegisteredKafkaIT {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

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
    void userRegisteredEventFromBrokerCreatesProfileAndPreferences() throws Exception {
        UUID userId = UUID.randomUUID();
        FitSyncDomainEvent<UserRegisteredPayload> event = new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "UserRegistered",
                1,
                Instant.now(),
                "auth-service",
                userId,
                new UserRegisteredPayload(
                        "kafka-it@example.com",
                        "Kafka IT User",
                        "USER",
                        Instant.now()));

        kafkaTemplate.send("fitsync-user-events-it", userId.toString(), objectMapper.writeValueAsString(event)).get();

        assertThat(eventuallyProvisioned(userId)).isTrue();
        assertThat(userProfileRepository.findById(userId))
                .isPresent()
                .get()
                .extracting(profile -> profile.getDisplayName())
                .isEqualTo("Kafka IT User");
        assertThat(userAppPreferencesRepository.findById(userId)).isPresent();
        assertThat(userTrainingPreferencesRepository.findById(userId)).isPresent();
        assertThat(userNutritionPreferencesRepository.findById(userId)).isPresent();
        assertThat(userNotificationPreferencesRepository.findById(userId)).isPresent();
    }

    private boolean eventuallyProvisioned(UUID userId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            if (userProfileRepository.existsById(userId)
                    && userAppPreferencesRepository.existsById(userId)
                    && userTrainingPreferencesRepository.existsById(userId)
                    && userNutritionPreferencesRepository.existsById(userId)
                    && userNotificationPreferencesRepository.existsById(userId)) {
                return true;
            }
            Thread.sleep(250);
        }
        return false;
    }
}
