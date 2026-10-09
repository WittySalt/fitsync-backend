package com.fitsync.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Consent to process health answers: when it was given, and that withdrawing it stops the coach's view of the
 * readiness questionnaire while the user's own view keeps working.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserHealthDataConsentTests {

    private static final String COACH_CONTEXT = "/api/v1/users/me/health-readiness/coach-context";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void onboardingWithoutTheConsentFieldLeavesConsentOffAsBefore() throws Exception {
        String token = token();

        onboard(token, "");

        mockMvc.perform(get("/api/v1/users/me/preferences").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.dataSharingConsent").value(false))
                .andExpect(jsonPath("$.app.healthDataConsentAt").value(nullValue()));
    }

    @Test
    void onboardingWithConsentRecordsWhenItWasGiven() throws Exception {
        String token = token();

        onboard(token, "\"healthDataConsent\": true,");

        mockMvc.perform(get("/api/v1/users/me/preferences").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.dataSharingConsent").value(true))
                .andExpect(jsonPath("$.app.healthDataConsentAt").isNotEmpty());
    }

    @Test
    void givingConsentStampsTheTimeKeepingItLeavesTheStampAndWithdrawingClearsIt() throws Exception {
        String token = token();

        String givenAt = putConsent(token, true).path("app").path("healthDataConsentAt").asText(null);
        assertThat(givenAt).isNotBlank();

        String keptAt = putConsent(token, true).path("app").path("healthDataConsentAt").asText(null);
        assertThat(keptAt).isEqualTo(givenAt);

        JsonNode withdrawn = putConsent(token, false).path("app");
        assertThat(withdrawn.path("dataSharingConsent").asBoolean()).isFalse();
        assertThat(withdrawn.path("healthDataConsentAt").isNull()).isTrue();
    }

    @Test
    void theCoachSeesInjuredAreasOnlyWhileConsentIsGiven() throws Exception {
        String token = token();
        mockMvc.perform(put("/api/v1/users/me/health-readiness")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "heartCondition": true,
                                  "chestPainActivity": false,
                                  "dizziness": false,
                                  "boneJointIssue": true,
                                  "bpOrHeartMeds": false,
                                  "otherReason": false,
                                  "injuryAreas": ["LEFT_KNEE"],
                                  "acknowledgedDisclaimerAt": "2026-06-20T09:15:00Z"
                                }
                                """))
                .andExpect(status().isOk());

        // No consent yet: nothing is released to the coach.
        mockMvc.perform(get(COACH_CONTEXT).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthDataConsent").value(false))
                .andExpect(jsonPath("$.injuryAreas", empty()))
                .andExpect(jsonPath("$.heartCondition").doesNotExist());

        putConsent(token, true);
        mockMvc.perform(get(COACH_CONTEXT).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthDataConsent").value(true))
                .andExpect(jsonPath("$.injuryAreas[0]").value("LEFT_KNEE"))
                .andExpect(jsonPath("$.heartCondition").doesNotExist());

        putConsent(token, false);
        mockMvc.perform(get(COACH_CONTEXT).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthDataConsent").value(false))
                .andExpect(jsonPath("$.injuryAreas", empty()));

        // The user's own screen still shows the answers after withdrawal.
        mockMvc.perform(get("/api/v1/users/me/health-readiness").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.injuryAreas[0]").value("LEFT_KNEE"));
    }

    @Test
    void theCoachContextRequiresAuthentication() throws Exception {
        mockMvc.perform(get(COACH_CONTEXT))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode putConsent(String token, boolean consent) throws Exception {
        String body = mockMvc.perform(put("/api/v1/users/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "app": {
                                    "preferredUnits": "100001",
                                    "language": "110001",
                                    "timezone": "Asia/Singapore",
                                    "privacyLevel": "120002",
                                    "dataSharingConsent": %s
                                  },
                                  "training": {
                                    "fitnessLevel": "200002",
                                    "trainingLocation": "210002",
                                    "preferredDurationMinutes": 45,
                                    "intensityPreference": "220003"
                                  },
                                  "nutrition": {
                                    "dietaryPattern": "300002",
                                    "mealFrequency": 3,
                                    "calorieTrackingMode": "310003",
                                    "macroPreference": "320002"
                                  },
                                  "notifications": {
                                    "pushEnabled": false,
                                    "emailEnabled": false,
                                    "workoutReminderEnabled": false,
                                    "mealReminderEnabled": false,
                                    "weighInReminderEnabled": false,
                                    "goalProgressReminderEnabled": false,
                                    "weeklyProgressReportEnabled": false,
                                    "marketingEmailsEnabled": false
                                  }
                                }
                                """.formatted(consent)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private void onboard(String token, String consentField) throws Exception {
        mockMvc.perform(post("/api/v1/users/me/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  %s
                                  "displayName": "Consent Check",
                                  "birthDate": "1995-03-04",
                                  "heightCm": 170.0,
                                  "activityLevel": "MODERATELY_ACTIVE",
                                  "timezone": "Asia/Singapore",
                                  "preferredUnits": "100001",
                                  "currentWeightKg": 70.0,
                                  "targetWeightKg": 68.0,
                                  "fitnessLevel": "200001",
                                  "trainingLocation": "210004",
                                  "preferredDurationMinutes": 45,
                                  "intensityPreference": "220002",
                                  "dietaryPattern": "300001",
                                  "mealFrequency": 3,
                                  "calorieTrackingMode": "310002",
                                  "macroPreference": "320002",
                                  "workoutReminderEnabled": false,
                                  "mealReminderEnabled": false,
                                  "weighInReminderEnabled": false
                                }
                                """.formatted(consentField)))
                .andExpect(status().isOk());
    }

    private static String token() {
        return Jwts.builder()
                .claims(Map.of(
                        "userId", UUID.randomUUID().toString(),
                        "displayName", "Consent Check",
                        "role", "USER"))
                .subject("consent-" + UUID.randomUUID() + "@example.com")
                .signWith(Keys.hmacShaKeyFor(
                        "test-secret-test-secret-test-secret-123456".getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
