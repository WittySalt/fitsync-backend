package com.fitsync.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.UserRegisteredPayload;
import com.fitsync.user.messaging.UserRegisteredEventConsumer;
import com.fitsync.user.messaging.entity.OutboxEventEntity;
import com.fitsync.user.messaging.repository.OutboxEventRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * NotificationPreferencesUpdated is published whenever a fact notification-service depends on
 * changes, and only then, with the whole current state on user-events keyed by the user id.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationPreferencesEventTests {

    private static final String EVENT_TYPE = "NotificationPreferencesUpdated";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private UserRegisteredEventConsumer userRegisteredEventConsumer;

    @BeforeEach
    void clearOutbox() {
        outboxEventRepository.deleteAll();
    }

    @Test
    void savingPreferencesPublishesTheWholeNotificationState() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = tokenFor(userId);

        putPreferences(token, "22:00:00", "07:00:00", "19:30:00", 2500);

        List<OutboxEventEntity> events = notificationEvents(userId);
        assertEquals(1, events.size());
        OutboxEventEntity event = events.get(0);
        assertEquals("user-events", event.getTopic());
        assertEquals(userId.toString(), event.getAggregateId());
        JsonNode payload = objectMapper.readTree(event.getPayload()).get("payload");
        assertTrue(payload.get("emailEnabled").asBoolean());
        assertTrue(payload.get("workoutReminderEnabled").asBoolean());
        assertTrue(payload.get("goalProgressReminderEnabled").asBoolean());
        assertTrue(payload.get("weeklyProgressReportEnabled").asBoolean());
        assertEquals("EMAIL", payload.get("weeklyProgressReportChannel").asText());
        assertEquals("22:00", payload.get("quietHoursStart").asText());
        assertEquals("07:00", payload.get("quietHoursEnd").asText());
        assertEquals("19:30", payload.get("preferredWorkoutTime").asText());
        assertTrue(payload.get("timezone").isNull());
    }

    @Test
    void savingTheSamePreferencesOrOnlyUnrelatedFieldsPublishesNothing() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = tokenFor(userId);
        putPreferences(token, "22:00:00", "07:00:00", "19:30:00", 2500);
        outboxEventRepository.deleteAll();

        putPreferences(token, "22:00:00", "07:00:00", "19:30:00", 2500);
        putPreferences(token, "22:00:00", "07:00:00", "19:30:00", 3000);

        assertTrue(notificationEvents(userId).isEmpty());
    }

    @Test
    void changingQuietHoursOrPreferredWorkoutTimePublishesAgain() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = tokenFor(userId);
        putPreferences(token, "22:00:00", "07:00:00", "19:30:00", 2500);
        outboxEventRepository.deleteAll();

        putPreferences(token, "23:15:00", "07:00:00", "19:30:00", 2500);
        putPreferences(token, "23:15:00", "07:00:00", "06:45:00", 2500);

        List<OutboxEventEntity> events = notificationEvents(userId);
        assertEquals(2, events.size());
        assertTrue(events.stream().anyMatch(event -> event.getPayload().contains("\"quietHoursStart\":\"23:15\"")
                && event.getPayload().contains("\"preferredWorkoutTime\":\"19:30\"")));
        assertTrue(events.stream().anyMatch(event -> event.getPayload().contains("\"preferredWorkoutTime\":\"06:45\"")));
    }

    @Test
    void profileTimezoneChangePublishesButOtherProfileEditsDoNot() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = tokenFor(userId);
        putPreferences(token, null, null, null, 2500);
        outboxEventRepository.deleteAll();

        putProfile(token, "Notify User", "Europe/London");
        assertEquals(1, notificationEvents(userId).size());
        assertTrue(notificationEvents(userId).get(0).getPayload().contains("\"timezone\":\"Europe/London\""));
        outboxEventRepository.deleteAll();

        putProfile(token, "Renamed User", "Europe/London");
        assertTrue(notificationEvents(userId).isEmpty());
    }

    @Test
    void readingPreferencesThatAlreadyExistPublishesNothing() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = tokenFor(userId);
        mockMvc.perform(get("/api/v1/users/me/preferences").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertEquals(1, notificationEvents(userId).size());
        outboxEventRepository.deleteAll();

        mockMvc.perform(get("/api/v1/users/me/preferences").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertTrue(notificationEvents(userId).isEmpty());
    }

    @Test
    void onboardingPublishesTheTimezoneAndPreferredWorkoutTime() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = tokenFor(userId);

        mockMvc.perform(post("/api/v1/users/me/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "displayName": "Onboarded User",
                                  "birthDate": "1996-04-12",
                                  "gender": "FEMALE",
                                  "heightCm": 168.0,
                                  "activityLevel": "MODERATELY_ACTIVE",
                                  "timezone": "Asia/Tokyo",
                                  "preferredUnits": "100001",
                                  "currentWeightKg": 72.0,
                                  "targetWeightKg": 68.0,
                                  "targetDate": "2026-12-30",
                                  "fitnessLevel": "200001",
                                  "trainingLocation": "210004",
                                  "weeklyWorkoutDays": 4,
                                  "preferredDurationMinutes": 45,
                                  "preferredWorkoutTime": "07:15:00",
                                  "intensityPreference": "220002",
                                  "dietaryPattern": "300001",
                                  "mealFrequency": 4,
                                  "calorieTrackingMode": "310002",
                                  "macroPreference": "320002",
                                  "waterGoalMl": 2500,
                                  "workoutReminderEnabled": true,
                                  "mealReminderEnabled": false,
                                  "weighInReminderEnabled": false
                                }
                                """))
                .andExpect(status().isOk());

        List<OutboxEventEntity> events = notificationEvents(userId);
        assertTrue(events.stream().anyMatch(event -> event.getPayload().contains("\"timezone\":\"Asia/Tokyo\"")
                && event.getPayload().contains("\"preferredWorkoutTime\":\"07:15\"")
                && event.getPayload().contains("\"workoutReminderEnabled\":true")));
    }

    @Test
    void registrationProvisioningPublishesOnceAndAReplayPublishesNothing() {
        UUID userId = UUID.randomUUID();
        FitSyncDomainEvent<UserRegisteredPayload> registered = new FitSyncDomainEvent<>(
                UUID.randomUUID(), "UserRegistered", 1, Instant.parse("2026-10-08T08:00:00Z"), "auth-service", userId,
                new UserRegisteredPayload("notify-new@example.com", "Notify New", "USER",
                        Instant.parse("2026-10-08T08:00:00Z")));

        userRegisteredEventConsumer.consume(registered);
        userRegisteredEventConsumer.consume(registered);

        List<OutboxEventEntity> events = notificationEvents(userId);
        assertEquals(1, events.size());
        assertTrue(events.get(0).getPayload().contains("\"emailEnabled\":false"));
        assertTrue(events.get(0).getPayload().contains("\"workoutReminderEnabled\":true"));
    }

    @Test
    void adminPersonFactsReconcileRepublishesNotificationPreferences() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = tokenFor(userId);
        // The republish walks the stored profiles, so the user needs one.
        putProfile(token, "Reconciled User", "Asia/Singapore");
        putPreferences(token, "22:00:00", "07:00:00", "19:30:00", 2500);
        outboxEventRepository.deleteAll();

        mockMvc.perform(post("/api/v1/internal/profiles/reconcile").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationPreferencesPublished").isNumber());

        List<OutboxEventEntity> events = notificationEvents(userId);
        assertEquals(1, events.size());
        assertTrue(events.get(0).getPayload().contains("\"quietHoursStart\":\"22:00\""));
    }

    private List<OutboxEventEntity> notificationEvents(UUID userId) {
        return outboxEventRepository.findAll().stream()
                .filter(event -> EVENT_TYPE.equals(event.getEventType()))
                .filter(event -> userId.equals(event.getUserId()))
                .toList();
    }

    private void putProfile(String token, String displayName, String timezone) throws Exception {
        mockMvc.perform(put("/api/v1/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {"displayName": "%s", "timezone": "%s", "onboardingCompleted": true}
                                """.formatted(displayName, timezone)))
                .andExpect(status().isOk());
    }

    private void putPreferences(String token, String quietStart, String quietEnd, String workoutTime, int waterGoalMl)
            throws Exception {
        mockMvc.perform(put("/api/v1/users/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "app": {"preferredUnits": "100002", "language": "110002", "timezone": "Asia/Singapore",
                                          "privacyLevel": "120002", "dataSharingConsent": true},
                                  "training": {"fitnessLevel": "200002", "trainingLocation": "210002", "weeklyWorkoutDays": 3,
                                               "preferredDurationMinutes": 45, "preferredWorkoutTime": %s,
                                               "intensityPreference": "220003", "equipmentInventory": ["230002"],
                                               "focusAreas": ["240001"]},
                                  "nutrition": {"dietaryPattern": "300003", "mealFrequency": 3,
                                                "calorieTrackingMode": "310003", "macroPreference": "320002",
                                                "waterGoalMl": %d},
                                  "notifications": {"pushEnabled": true, "emailEnabled": true, "workoutReminderEnabled": true,
                                                    "mealReminderEnabled": true, "weighInReminderEnabled": true,
                                                    "goalProgressReminderEnabled": true, "weeklyProgressReportEnabled": true,
                                                    "weeklyProgressReportChannel": "EMAIL", "marketingEmailsEnabled": false,
                                                    "quietHoursStart": %s, "quietHoursEnd": %s}
                                }
                                """.formatted(quoted(workoutTime), waterGoalMl, quoted(quietStart), quoted(quietEnd))))
                .andExpect(status().isOk());
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static String tokenFor(UUID userId) {
        return Jwts.builder()
                .claims(Map.of(
                        "userId", userId.toString(),
                        "displayName", "Notify User",
                        "role", "USER"))
                .subject("notify-" + userId + "@example.com")
                .signWith(Keys.hmacShaKeyFor(
                        "test-secret-test-secret-test-secret-123456".getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
