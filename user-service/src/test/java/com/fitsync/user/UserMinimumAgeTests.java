package com.fitsync.user;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Every endpoint that sets a birth date refuses people under 16 and dates in the future. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserMinimumAgeTests {

    private static final String TOO_YOUNG = "birthDate: You must be at least 16 years old to use FitSync";
    private static final String IN_FUTURE = "birthDate: Birth date cannot be in the future";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void profileUpdateRejectsSomeoneUnderSixteen() throws Exception {
        putProfile(utcToday().minusYears(15))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details", hasItem(TOO_YOUNG)));
    }

    @Test
    void profileUpdateRejectsABirthDateInTheFuture() throws Exception {
        putProfile(utcToday().plusDays(2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details", hasItem(IN_FUTURE)));
    }

    @Test
    void profileUpdateAcceptsSomeoneTurningSixteenToday() throws Exception {
        putProfile(utcToday().minusYears(16))
                .andExpect(status().isOk());
    }

    @Test
    void onboardingRejectsSomeoneUnderSixteen() throws Exception {
        postOnboarding(utcToday().minusYears(16).plusDays(2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details", hasItem(TOO_YOUNG)));
    }

    @Test
    void onboardingAcceptsAnAdult() throws Exception {
        postOnboarding(LocalDate.of(1996, 4, 12))
                .andExpect(status().isOk());
    }

    private ResultActions putProfile(LocalDate birthDate) throws Exception {
        return mockMvc.perform(put("/api/v1/users/me/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token())
                .content("""
                        {
                          "displayName": "Age Check",
                          "birthDate": "%s",
                          "onboardingCompleted": false
                        }
                        """.formatted(birthDate)));
    }

    private ResultActions postOnboarding(LocalDate birthDate) throws Exception {
        return mockMvc.perform(post("/api/v1/users/me/onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token())
                .content("""
                        {
                          "displayName": "Age Check",
                          "birthDate": "%s",
                          "gender": "FEMALE",
                          "heightCm": 168.0,
                          "activityLevel": "MODERATELY_ACTIVE",
                          "timezone": "Asia/Singapore",
                          "preferredUnits": "100001",
                          "currentWeightKg": 60.0,
                          "targetWeightKg": 58.0,
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
                        """.formatted(birthDate)));
    }

    /** The UTC date never runs ahead of the validator's UTC+14 date, so boundaries computed from it are safe. */
    private static LocalDate utcToday() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static String token() {
        return Jwts.builder()
                .claims(Map.of(
                        "userId", UUID.randomUUID().toString(),
                        "displayName", "Age Check",
                        "role", "USER"))
                .subject("age-check-" + UUID.randomUUID() + "@example.com")
                .signWith(Keys.hmacShaKeyFor(
                        "test-secret-test-secret-test-secret-123456".getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
