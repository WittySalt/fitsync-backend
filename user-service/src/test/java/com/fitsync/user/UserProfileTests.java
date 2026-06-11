package com.fitsync.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fitsync.user.messaging.OutboxEventStatus;
import com.fitsync.user.messaging.repository.OutboxEventRepository;
import com.fitsync.user.repository.UserProfileRepository;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserProfileTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void clearOutbox() {
        outboxEventRepository.deleteAll();
    }

    @Test
    void protectedUserProfileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"));
    }

    @Test
    void validTokenCanAccessCurrentUserProfile() throws Exception {
        String userId = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .claims(Map.of(
                        "userId", userId,
                        "displayName", "Bob",
                        "role", "USER"))
                .subject("bob@example.com")
                .signWith(Keys.hmacShaKeyFor("test-secret-test-secret-test-secret-123456".getBytes(StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.email").value("bob@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.profile.displayName").value("Bob"))
                .andExpect(jsonPath("$.preferences.app.preferredUnits").value("100001"))
                .andExpect(jsonPath("$.preferences.training.fitnessLevel").value("200001"))
                .andExpect(jsonPath("$.preferences.nutrition.dietaryPattern").value("300001"))
                .andExpect(jsonPath("$.preferences.notifications.pushEnabled").value(true));
    }

    @Test
    void canUpdateSplitPreferencesWithCodeStableValues() throws Exception {
        String token = tokenFor("prefs@example.com", "Prefs User");

        mockMvc.perform(put("/api/v1/users/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "app": {
                                    "preferredUnits": "100002",
                                    "language": "110002",
                                    "timezone": "Asia/Singapore",
                                    "privacyLevel": "120002",
                                    "dataSharingConsent": true
                                  },
                                  "training": {
                                    "fitnessLevel": "200002",
                                    "trainingLocation": "210002",
                                    "weeklyWorkoutDays": 5,
                                    "preferredDurationMinutes": 45,
                                    "preferredWorkoutTime": "19:30:00",
                                    "intensityPreference": "220003"
                                  },
                                  "nutrition": {
                                    "dietaryPattern": "300002",
                                    "mealFrequency": 4,
                                    "calorieTrackingMode": "310003",
                                    "macroPreference": "320002",
                                    "waterGoalMl": 2500
                                  },
                                  "notifications": {
                                    "pushEnabled": true,
                                    "emailEnabled": true,
                                    "workoutReminderEnabled": true,
                                    "mealReminderEnabled": true,
                                    "weighInReminderEnabled": true,
                                    "goalProgressReminderEnabled": true,
                                    "marketingEmailsEnabled": false,
                                    "quietHoursStart": "22:00:00",
                                    "quietHoursEnd": "07:00:00"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.preferredUnits").value("100002"))
                .andExpect(jsonPath("$.app.language").value("110002"))
                .andExpect(jsonPath("$.training.trainingLocation").value("210002"))
                .andExpect(jsonPath("$.nutrition.dietaryPattern").value("300002"))
                .andExpect(jsonPath("$.notifications.emailEnabled").value(true));

        mockMvc.perform(get("/api/v1/users/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.privacyLevel").value("120002"))
                .andExpect(jsonPath("$.training.weeklyWorkoutDays").value(5))
                .andExpect(jsonPath("$.nutrition.waterGoalMl").value(2500))
                .andExpect(jsonPath("$.notifications.quietHoursStart").value("22:00:00"));
    }

    @Test
    void canUpdateProfileAndDerivedCurrentUserView() throws Exception {
        String token = tokenFor("carol@example.com", "Carol");
        mockMvc.perform(put("/api/v1/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "displayName": "Carol",
                                  "firstName": "Carol",
                                  "lastName": "Lim",
                                  "heightCm": 170.0,
                                  "activityLevel": "VERY_ACTIVE",
                                  "timezone": "Asia/Singapore",
                                  "bio": "Training for consistency",
                                  "onboardingCompleted": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Carol"))
                .andExpect(jsonPath("$.heightCm").value(170.0))
                .andExpect(jsonPath("$.activityLevel").value("VERY_ACTIVE"));

        mockMvc.perform(get("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.lastName").value("Lim"))
                .andExpect(jsonPath("$.profile.onboardingCompleted").value(true));
    }

    @Test
    void canRecordBodyMetricsAndReturnDerivedBmi() throws Exception {
        String token = tokenFor("dave@example.com", "Dave");

        mockMvc.perform(put("/api/v1/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "displayName": "Dave",
                                  "heightCm": 180.0,
                                  "onboardingCompleted": false
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/users/me/body-metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "weightKg": 85.0,
                                  "bodyFatRate": 21.5,
                                  "waistCm": 90.0,
                                  "measuredAt": "2026-06-11T20:15:00Z",
                                  "source": "MANUAL",
                                  "note": "Morning check-in"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.weightKg").value(85.0))
                .andExpect(jsonPath("$.bmi").value(26.23));

        mockMvc.perform(get("/api/v1/users/me/body-metrics/latest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bodyFatRate").value(21.5))
                .andExpect(jsonPath("$.source").value("MANUAL"));
    }

    @Test
    void canReadReplaceAndDeleteOwnedBodyMetric() throws Exception {
        String token = tokenFor("body-owner@example.com", "Body Owner");

        mockMvc.perform(put("/api/v1/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "displayName": "Body Owner",
                                  "heightCm": 175.0,
                                  "onboardingCompleted": true
                                }
                                """))
                .andExpect(status().isOk());

        String metricId = createMetric(token, """
                {
                  "weightKg": 80.0,
                  "bodyFatRate": 19.0,
                  "measuredAt": "2026-02-01T08:00:00Z",
                  "source": "MANUAL",
                  "note": "Initial"
                }
                """);

        mockMvc.perform(get("/api/v1/users/me/body-metrics/{metricId}", metricId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metricId").value(metricId))
                .andExpect(jsonPath("$.weightKg").value(80.0));

        mockMvc.perform(put("/api/v1/users/me/body-metrics/{metricId}", metricId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "weightKg": 78.0,
                                  "bodyFatRate": 18.5,
                                  "waistCm": 82.0,
                                  "measuredAt": "2026-02-02T08:00:00Z",
                                  "source": "MANUAL",
                                  "note": "Corrected"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metricId").value(metricId))
                .andExpect(jsonPath("$.weightKg").value(78.0))
                .andExpect(jsonPath("$.bmi").value(25.47))
                .andExpect(jsonPath("$.note").value("Corrected"));

        mockMvc.perform(delete("/api/v1/users/me/body-metrics/{metricId}", metricId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me/body-metrics/{metricId}", metricId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Body metric not found"));
    }

    @Test
    void bodyMetricSingleRecordAccessIsScopedToCurrentUser() throws Exception {
        String ownerToken = tokenFor("owner@example.com", "Owner");
        String otherToken = tokenFor("other@example.com", "Other");
        String metricId = createMetric(ownerToken, """
                {
                  "weightKg": 82.0,
                  "measuredAt": "2026-02-03T08:00:00Z",
                  "source": "MANUAL"
                }
                """);

        mockMvc.perform(get("/api/v1/users/me/body-metrics/{metricId}", metricId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Body metric not found"));

        mockMvc.perform(delete("/api/v1/users/me/body-metrics/{metricId}", metricId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void canFilterBodyMetricHistoryAndReturnTrends() throws Exception {
        String token = tokenFor("trend@example.com", "Trend User");

        mockMvc.perform(put("/api/v1/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "displayName": "Trend User",
                                  "heightCm": 180.0,
                                  "onboardingCompleted": true
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/users/me/body-metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "weightKg": 90.0,
                                  "bodyFatRate": 22.0,
                                  "waistCm": 95.0,
                                  "measuredAt": "2026-01-01T08:00:00Z",
                                  "source": "MANUAL"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users/me/body-metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "weightKg": 85.0,
                                  "bodyFatRate": 20.0,
                                  "waistCm": 90.0,
                                  "measuredAt": "2026-01-03T08:00:00Z",
                                  "source": "MANUAL"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/users/me/body-metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-01-02T00:00:00Z")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].weightKg").value(85.0));

        mockMvc.perform(get("/api/v1/users/me/body-metrics/trends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sampleCount").value(2))
                .andExpect(jsonPath("$.weightKg.first.value").value(90.0))
                .andExpect(jsonPath("$.weightKg.latest.value").value(85.0))
                .andExpect(jsonPath("$.weightKg.change").value(-5.0))
                .andExpect(jsonPath("$.bmi.first.value").value(27.78))
                .andExpect(jsonPath("$.bmi.latest.value").value(26.23));
    }

    @Test
    void canUpsertGoal() throws Exception {
        String token = tokenFor("emma@example.com", "Emma");

        mockMvc.perform(put("/api/v1/users/me/goals/WEIGHT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "targetValue": 68.0,
                                  "targetUnit": "kg",
                                  "targetDate": "2026-09-01",
                                  "status": "ACTIVE",
                                  "notes": "Cutting phase"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalType").value("WEIGHT"))
                .andExpect(jsonPath("$.targetValue").value(68.0));

        mockMvc.perform(get("/api/v1/users/me/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].goalType").value("WEIGHT"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void canFilterGoalsByStatus() throws Exception {
        String token = tokenFor("goals@example.com", "Goal User");

        mockMvc.perform(put("/api/v1/users/me/goals/WEIGHT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "targetValue": 68.0,
                                  "targetUnit": "kg",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/users/me/goals/STEPS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "targetValue": 10000.0,
                                  "targetUnit": "steps",
                                  "status": "PAUSED"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].goalType").value("WEIGHT"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void bodyMetricsRequiresAtLeastOneMeasurement() throws Exception {
        String token = tokenFor("frank@example.com", "Frank");

        mockMvc.perform(post("/api/v1/users/me/body-metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "measuredAt": "2026-06-11T20:15:00Z",
                                  "source": "MANUAL"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("At least one body measurement must be provided"));
    }

    @Test
    void bodyMetricHistoryRejectsInvalidQueryParams() throws Exception {
        String token = tokenFor("invalid-query@example.com", "Invalid Query");

        mockMvc.perform(get("/api/v1/users/me/body-metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .param("limit", "501"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));

        mockMvc.perform(get("/api/v1/users/me/body-metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-03-02T00:00:00Z")
                        .param("to", "2026-03-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("from must be before or equal to to"));
    }

    @Test
    void adminCanRelayPendingUserOutboxEvents() throws Exception {
        String token = tokenFor("grace@example.com", "Grace");

        mockMvc.perform(put("/api/v1/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "displayName": "Grace",
                                  "heightCm": 168.0,
                                  "onboardingCompleted": true
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/internal/outbox/publish")
                        .with(user("admin").roles("ADMIN"))
                        .param("batchSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedBatchSize").value(10))
                .andExpect(jsonPath("$.publishedCount").value(1));

        org.assertj.core.api.Assertions.assertThat(
                        outboxEventRepository.findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PUBLISHED))
                .hasSize(1);
    }

    @Test
    void internalRelayRequiresAdminRoleWithJsonError() throws Exception {
        mockMvc.perform(post("/api/v1/internal/outbox/publish")
                        .with(user("plain-user").roles("USER"))
                        .param("batchSize", "10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access is denied"));
    }

    private String tokenFor(String email, String displayName) {
        return Jwts.builder()
                .claims(Map.of(
                        "userId", UUID.randomUUID().toString(),
                        "displayName", displayName,
                        "role", "USER"))
                .subject(email)
                .signWith(Keys.hmacShaKeyFor("test-secret-test-secret-test-secret-123456".getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private String createMetric(String token, String payload) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/users/me/body-metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("metricId").asText();
    }
}
