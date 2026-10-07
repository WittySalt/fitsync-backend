package com.fitsync.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
import com.fitsync.user.entity.UserProfileEntity;
import com.fitsync.user.messaging.OutboxEventStatus;
import com.fitsync.user.messaging.repository.OutboxEventRepository;
import com.fitsync.user.domain.GoalStatus;
import com.fitsync.user.repository.UserGoalRepository;
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

    @Autowired
    private UserGoalRepository userGoalRepository;

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
                .signWith(Keys.hmacShaKeyFor(
                        "test-secret-test-secret-test-secret-123456".getBytes(StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.email").value("bob@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.profile.displayName").value("Bob"))
                .andExpect(jsonPath("$.profile.membershipTier").value("MEMBER"))
                .andExpect(jsonPath("$.profile.membershipStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.profile.primaryGoal").value(nullValue()))
                .andExpect(jsonPath("$.profile.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.preferences.app.preferredUnits").value("100001"))
                .andExpect(jsonPath("$.preferences.training.fitnessLevel").value("200001"))
                .andExpect(jsonPath("$.preferences.nutrition.dietaryPattern").value("300001"))
                .andExpect(jsonPath("$.preferences.notifications.pushEnabled").value(true))
                .andExpect(jsonPath("$.preferences.notifications.weeklyProgressReportEnabled").value(false))
                .andExpect(jsonPath("$.preferences.notifications.weeklyProgressReportChannel").value("EMAIL"))
                .andExpect(jsonPath("$.preferences.notifications.deliveryActive").value(false))
                .andExpect(jsonPath("$.preferences.notifications.deliveryStatus").value("NOT_YET_ACTIVE"));
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
                                  "intensityPreference": "220003",
                                  "equipmentInventory": [
                                    "230002",
                                    "230004",
                                    "230006"
                                  ],
                                  "focusAreas": [
                                    "240001",
                                    "240005"
                                  ]
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
                                    "weeklyProgressReportEnabled": true,
                                    "weeklyProgressReportChannel": "EMAIL",
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
                .andExpect(jsonPath("$.training.equipmentInventory", hasSize(3)))
                .andExpect(jsonPath("$.training.equipmentInventory[0]").value("230002"))
                .andExpect(jsonPath("$.training.focusAreas", hasSize(2)))
                .andExpect(jsonPath("$.training.focusAreas[1]").value("240005"))
                .andExpect(jsonPath("$.nutrition.dietaryPattern").value("300002"))
                .andExpect(jsonPath("$.notifications.emailEnabled").value(true))
                .andExpect(jsonPath("$.notifications.weeklyProgressReportEnabled").value(true))
                .andExpect(jsonPath("$.notifications.weeklyProgressReportChannel").value("EMAIL"))
                .andExpect(jsonPath("$.notifications.deliveryActive").value(false))
                .andExpect(jsonPath("$.notifications.deliveryStatus").value("NOT_YET_ACTIVE"));

        mockMvc.perform(get("/api/v1/users/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.privacyLevel").value("120002"))
                .andExpect(jsonPath("$.training.weeklyWorkoutDays").value(5))
                .andExpect(jsonPath("$.training.equipmentInventory[2]").value("230006"))
                .andExpect(jsonPath("$.training.focusAreas[0]").value("240001"))
                .andExpect(jsonPath("$.nutrition.waterGoalMl").value(2500))
                .andExpect(jsonPath("$.notifications.quietHoursStart").value("22:00:00"))
                .andExpect(jsonPath("$.notifications.weeklyProgressReportEnabled").value(true));
    }

    @Test
    void canManageHealthReadinessWithoutLeakingIntoCurrentUserView() throws Exception {
        String token = tokenFor("health@example.com", "Health User");

        mockMvc.perform(put("/api/v1/users/me/health-readiness")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "heartCondition": true,
                                  "chestPainActivity": false,
                                  "dizziness": true,
                                  "boneJointIssue": true,
                                  "bpOrHeartMeds": false,
                                  "otherReason": false,
                                  "injuryAreas": [
                                    "LEFT_KNEE",
                                    "LOWER_BACK"
                                  ],
                                  "acknowledgedDisclaimerAt": "2026-06-20T09:15:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heartCondition").value(true))
                .andExpect(jsonPath("$.dizziness").value(true))
                .andExpect(jsonPath("$.injuryAreas", hasSize(2)))
                .andExpect(jsonPath("$.injuryAreas[1]").value("LOWER_BACK"))
                .andExpect(jsonPath("$.acknowledgedDisclaimerAt").value("2026-06-20T09:15:00Z"));

        mockMvc.perform(get("/api/v1/users/me/health-readiness")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boneJointIssue").value(true))
                .andExpect(jsonPath("$.injuryAreas[0]").value("LEFT_KNEE"));

        mockMvc.perform(get("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthReadiness").doesNotExist());
    }

    @Test
    void healthReadinessRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/health-readiness"))
                .andExpect(status().isUnauthorized());
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
                                  "locationDisplay": "Singapore",
                                  "bio": "Training for consistency",
                                  "onboardingCompleted": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Carol"))
                .andExpect(jsonPath("$.heightCm").value(170.0))
                .andExpect(jsonPath("$.activityLevel").value("VERY_ACTIVE"))
                .andExpect(jsonPath("$.locationDisplay").value("Singapore"))
                .andExpect(jsonPath("$.membershipTier").value("MEMBER"));

        mockMvc.perform(get("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.lastName").value("Lim"))
                .andExpect(jsonPath("$.profile.locationDisplay").value("Singapore"))
                .andExpect(jsonPath("$.profile.onboardingCompleted").value(true));
    }

    @Test
    void profileUpdateIgnoresClientSuppliedAvatarUrl() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = tokenFor(userId, "avatar-url-injection@example.com", "Avatar User");

        mockMvc.perform(put("/api/v1/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "displayName": "Avatar User",
                                  "avatarUrl": "http://evil.example/x.png",
                                  "heightCm": 170.0,
                                  "onboardingCompleted": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(nullValue()));

        String storedAvatarUrl = "https://media.fitsync.test/avatars/" + userId + "/avatar.png";
        UserProfileEntity profile = userProfileRepository.findById(userId).orElseThrow();
        profile.setAvatarUrl(storedAvatarUrl);
        userProfileRepository.save(profile);

        mockMvc.perform(put("/api/v1/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "displayName": "Avatar User Updated",
                                  "avatarUrl": "http://evil.example/replaced.png",
                                  "heightCm": 171.0,
                                  "onboardingCompleted": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Avatar User Updated"))
                .andExpect(jsonPath("$.avatarUrl").value(storedAvatarUrl));

        mockMvc.perform(get("/api/v1/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(storedAvatarUrl));
    }

    @Test
    void canCompleteOnboardingInSingleRequest() throws Exception {
        String token = tokenFor("new-user@example.com", "New User");

        mockMvc.perform(post("/api/v1/users/me/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "displayName": "New User",
                                  "birthDate": "1996-04-12",
                                  "gender": "FEMALE",
                                  "heightCm": 168.0,
                                  "activityLevel": "MODERATELY_ACTIVE",
                                  "timezone": "Asia/Singapore",
                                  "preferredUnits": "100001",
                                  "currentWeightKg": 72.0,
                                  "targetWeightKg": 68.0,
                                  "bodyFatRate": 22.5,
                                  "targetDate": "2026-09-30",
                                  "fitnessLevel": "200001",
                                  "trainingLocation": "210004",
                                  "weeklyWorkoutDays": 4,
                                  "preferredDurationMinutes": 45,
                                  "preferredWorkoutTime": "19:30:00",
                                  "intensityPreference": "220002",
                                  "dietaryPattern": "300001",
                                  "mealFrequency": 4,
                                  "calorieTrackingMode": "310002",
                                  "macroPreference": "320002",
                                  "waterGoalMl": 2500,
                                  "workoutReminderEnabled": true,
                                  "mealReminderEnabled": true,
                                  "weighInReminderEnabled": true,
                                  "primaryGoal": "BUILD_MUSCLE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.displayName").value("New User"))
                .andExpect(jsonPath("$.profile.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.profile.primaryGoal").value("BUILD_MUSCLE"))
                .andExpect(jsonPath("$.preferences.training.weeklyWorkoutDays").value(4))
                .andExpect(jsonPath("$.preferences.nutrition.macroPreference").value("320002"))
                .andExpect(jsonPath("$.latestBodyMetrics.weightKg").value(72.0))
                .andExpect(jsonPath("$.activeGoals", hasSize(6)))
                .andExpect(jsonPath("$.activeGoals[*].goalType", containsInAnyOrder(
                        "WEIGHT", "WORKOUT_FREQUENCY", "CALORIES", "PROTEIN", "CARBS", "FAT")));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.primaryGoal").value("BUILD_MUSCLE"));

        mockMvc.perform(get("/api/v1/users/me/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[*].goalType", containsInAnyOrder(
                        "WEIGHT", "WORKOUT_FREQUENCY", "CALORIES", "PROTEIN", "CARBS", "FAT")));
    }

    @Test
    void canCompleteOnboardingWithoutDecidingWeeklyWorkoutDays() throws Exception {
        String token = tokenFor("undecided-frequency@example.com", "Undecided User");

        mockMvc.perform(post("/api/v1/users/me/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "displayName": "Undecided User",
                                  "birthDate": "1996-04-12",
                                  "gender": "FEMALE",
                                  "heightCm": 168.0,
                                  "activityLevel": "MODERATELY_ACTIVE",
                                  "timezone": "Asia/Singapore",
                                  "preferredUnits": "100001",
                                  "currentWeightKg": 72.0,
                                  "targetWeightKg": 68.0,
                                  "bodyFatRate": 22.5,
                                  "targetDate": "2026-09-30",
                                  "fitnessLevel": "200001",
                                  "trainingLocation": "210004",
                                  "preferredDurationMinutes": 45,
                                  "preferredWorkoutTime": "19:30:00",
                                  "intensityPreference": "220002",
                                  "dietaryPattern": "300001",
                                  "mealFrequency": 4,
                                  "calorieTrackingMode": "310002",
                                  "macroPreference": "320002",
                                  "waterGoalMl": 2500,
                                  "workoutReminderEnabled": true,
                                  "mealReminderEnabled": true,
                                  "weighInReminderEnabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.preferences.training.weeklyWorkoutDays").value(nullValue()))
                .andExpect(jsonPath("$.activeGoals", hasSize(5)))
                .andExpect(jsonPath("$.activeGoals[*].goalType", containsInAnyOrder(
                        "WEIGHT", "CALORIES", "PROTEIN", "CARBS", "FAT")));

        mockMvc.perform(get("/api/v1/users/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.training.weeklyWorkoutDays").value(nullValue()));
    }

    @Test
    void secondOnboardingWithoutWeeklyWorkoutDaysKeepsTheEarlierGoal() throws Exception {
        String token = tokenFor("decides-later@example.com", "Decides Later");

        mockMvc.perform(post("/api/v1/users/me/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "displayName": "Decides Later",
                                  "birthDate": "1996-04-12",
                                  "gender": "FEMALE",
                                  "heightCm": 168.0,
                                  "activityLevel": "MODERATELY_ACTIVE",
                                  "timezone": "Asia/Singapore",
                                  "preferredUnits": "100001",
                                  "currentWeightKg": 72.0,
                                  "targetWeightKg": 68.0,
                                  "bodyFatRate": 22.5,
                                  "targetDate": "2026-09-30",
                                  "fitnessLevel": "200001",
                                  "trainingLocation": "210004",
                                  "weeklyWorkoutDays": 4,
                                  "preferredDurationMinutes": 45,
                                  "preferredWorkoutTime": "19:30:00",
                                  "intensityPreference": "220002",
                                  "dietaryPattern": "300001",
                                  "mealFrequency": 4,
                                  "calorieTrackingMode": "310002",
                                  "macroPreference": "320002",
                                  "waterGoalMl": 2500,
                                  "workoutReminderEnabled": true,
                                  "mealReminderEnabled": true,
                                  "weighInReminderEnabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeGoals", hasSize(6)));

        mockMvc.perform(post("/api/v1/users/me/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "displayName": "Decides Later",
                                  "birthDate": "1996-04-12",
                                  "gender": "FEMALE",
                                  "heightCm": 168.0,
                                  "activityLevel": "MODERATELY_ACTIVE",
                                  "timezone": "Asia/Singapore",
                                  "preferredUnits": "100001",
                                  "currentWeightKg": 70.0,
                                  "targetWeightKg": 66.0,
                                  "bodyFatRate": 21.0,
                                  "targetDate": "2026-10-31",
                                  "fitnessLevel": "200002",
                                  "trainingLocation": "210002",
                                  "preferredDurationMinutes": 50,
                                  "preferredWorkoutTime": "20:00:00",
                                  "intensityPreference": "220003",
                                  "dietaryPattern": "300002",
                                  "mealFrequency": 3,
                                  "calorieTrackingMode": "310003",
                                  "macroPreference": "320002",
                                  "waterGoalMl": 2600,
                                  "workoutReminderEnabled": true,
                                  "mealReminderEnabled": true,
                                  "weighInReminderEnabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences.training.weeklyWorkoutDays").value(nullValue()))
                .andExpect(jsonPath("$.activeGoals", hasSize(6)))
                .andExpect(jsonPath("$.activeGoals[*].goalType", containsInAnyOrder(
                        "WEIGHT", "WORKOUT_FREQUENCY", "CALORIES", "PROTEIN", "CARBS", "FAT")));

        MvcResult goalsResult = mockMvc.perform(get("/api/v1/users/me/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode goals = objectMapper.readTree(goalsResult.getResponse().getContentAsString());
        JsonNode workoutFrequencyGoal = null;
        for (JsonNode goal : goals) {
            if ("WORKOUT_FREQUENCY".equals(goal.get("goalType").asText())) {
                workoutFrequencyGoal = goal;
            }
        }
        assertNotNull(workoutFrequencyGoal,
                "the workout frequency goal set by the first onboarding must still be present");
        assertEquals(4.0, workoutFrequencyGoal.get("targetValue").asDouble());
    }

    @Test
    void onboardingRejectsWeeklyWorkoutDaysOutsideAllowedRangeButAcceptsMissingValue() throws Exception {
        String token = tokenFor("range-check@example.com", "Range Check");
        String bodyTemplate = """
                {
                  "displayName": "Range Check",
                  "heightCm": 168.0,
                  "activityLevel": "MODERATELY_ACTIVE",
                  "preferredUnits": "100001",
                  "currentWeightKg": 72.0,
                  "targetWeightKg": 68.0,
                  "fitnessLevel": "200001",
                  "trainingLocation": "210004",
                  "weeklyWorkoutDays": %s,
                  "preferredDurationMinutes": 45,
                  "intensityPreference": "220002",
                  "dietaryPattern": "300001",
                  "mealFrequency": 4,
                  "calorieTrackingMode": "310002",
                  "macroPreference": "320002"
                }
                """;

        mockMvc.perform(post("/api/v1/users/me/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(bodyTemplate.formatted(0)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/users/me/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(bodyTemplate.formatted(15)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completingOnboardingAgainUpdatesExistingBaselineRecords() throws Exception {
        String token = tokenFor("repeat-onboarding@example.com", "Repeat User");

        mockMvc.perform(post("/api/v1/users/me/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "displayName": "Repeat User",
                                  "birthDate": "1996-04-12",
                                  "gender": "FEMALE",
                                  "heightCm": 168.0,
                                  "activityLevel": "MODERATELY_ACTIVE",
                                  "timezone": "Asia/Singapore",
                                  "preferredUnits": "100001",
                                  "currentWeightKg": 72.0,
                                  "targetWeightKg": 68.0,
                                  "bodyFatRate": 22.5,
                                  "targetDate": "2026-09-30",
                                  "fitnessLevel": "200001",
                                  "trainingLocation": "210004",
                                  "weeklyWorkoutDays": 4,
                                  "preferredDurationMinutes": 45,
                                  "preferredWorkoutTime": "19:30:00",
                                  "intensityPreference": "220002",
                                  "dietaryPattern": "300001",
                                  "mealFrequency": 4,
                                  "calorieTrackingMode": "310002",
                                  "macroPreference": "320002",
                                  "waterGoalMl": 2500,
                                  "workoutReminderEnabled": true,
                                  "mealReminderEnabled": true,
                                  "weighInReminderEnabled": true
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/users/me/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "displayName": "Repeat Updated",
                                  "birthDate": "1996-04-12",
                                  "gender": "FEMALE",
                                  "heightCm": 168.0,
                                  "activityLevel": "MODERATELY_ACTIVE",
                                  "timezone": "Asia/Singapore",
                                  "preferredUnits": "100001",
                                  "currentWeightKg": 70.0,
                                  "targetWeightKg": 66.0,
                                  "bodyFatRate": 21.0,
                                  "targetDate": "2026-10-31",
                                  "fitnessLevel": "200002",
                                  "trainingLocation": "210002",
                                  "weeklyWorkoutDays": 5,
                                  "preferredDurationMinutes": 50,
                                  "preferredWorkoutTime": "20:00:00",
                                  "intensityPreference": "220003",
                                  "dietaryPattern": "300002",
                                  "mealFrequency": 3,
                                  "calorieTrackingMode": "310003",
                                  "macroPreference": "320002",
                                  "waterGoalMl": 2600,
                                  "workoutReminderEnabled": true,
                                  "mealReminderEnabled": true,
                                  "weighInReminderEnabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.displayName").value("Repeat Updated"))
                .andExpect(jsonPath("$.latestBodyMetrics.weightKg").value(70.0))
                .andExpect(jsonPath("$.activeGoals", hasSize(6)));

        mockMvc.perform(get("/api/v1/users/me/body-metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].weightKg").value(70.0));

        mockMvc.perform(get("/api/v1/users/me/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)));
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
    void canUpsertCarbAndFatGoals() throws Exception {
        String token = tokenFor("macro-goals@example.com", "Macro Goals");

        mockMvc.perform(put("/api/v1/users/me/goals/CARBS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "targetValue": 220.0,
                                  "targetUnit": "g",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalType").value("CARBS"))
                .andExpect(jsonPath("$.targetUnit").value("g"));

        mockMvc.perform(put("/api/v1/users/me/goals/FAT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "targetValue": 65.0,
                                  "targetUnit": "g",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalType").value("FAT"))
                .andExpect(jsonPath("$.targetUnit").value("g"));

        mockMvc.perform(get("/api/v1/users/me/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].goalType", containsInAnyOrder("CARBS", "FAT")));
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
    void canSoftDeleteGoal() throws Exception {
        String token = tokenFor("delete-goal@example.com", "Delete Goal");

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

        mockMvc.perform(delete("/api/v1/users/me/goals/WEIGHT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Soft-deleted: no longer in the ACTIVE list...
        mockMvc.perform(get("/api/v1/users/me/goals")
                        .header("Authorization", "Bearer " + token)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // ...but the row is kept as ARCHIVED so the slot can be reused on re-create.
        mockMvc.perform(get("/api/v1/users/me/goals")
                        .header("Authorization", "Bearer " + token)
                        .param("status", "ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].goalType").value("WEIGHT"))
                .andExpect(jsonPath("$[0].status").value("ARCHIVED"));
    }

    @Test
    void deletingGoalIsIdempotent() throws Exception {
        String token = tokenFor("delete-goal-twice@example.com", "Delete Twice");

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

        mockMvc.perform(delete("/api/v1/users/me/goals/WEIGHT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Second delete on an already-ARCHIVED goal is a no-op, still 204.
        mockMvc.perform(delete("/api/v1/users/me/goals/WEIGHT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/me/goals")
                        .header("Authorization", "Bearer " + token)
                        .param("status", "ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void deletingMissingGoalReturnsNotFound() throws Exception {
        String token = tokenFor("delete-missing-goal@example.com", "Delete Missing");

        mockMvc.perform(delete("/api/v1/users/me/goals/WEIGHT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void canRequestAndDownloadAccountDataExport() throws Exception {
        String token = tokenFor("export@example.com", "Export User");

        mockMvc.perform(put("/api/v1/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "displayName": "Export User",
                                  "heightCm": 171.0,
                                  "onboardingCompleted": true
                                }
                                """))
                .andExpect(status().isOk());

        String exportRequestId = objectMapper.readTree(mockMvc.perform(post("/api/v1/users/me/data-export/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.downloadAvailable").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString()).get("exportRequestId").asText();

        mockMvc.perform(get("/api/v1/users/me/data-export/requests/latest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportRequestId").value(exportRequestId))
                .andExpect(jsonPath("$.status").value("READY"));

        mockMvc.perform(get("/api/v1/users/me/data-export/requests/{exportRequestId}/download", exportRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.email").value("export@example.com"))
                .andExpect(jsonPath("$.account.profile.displayName").value("Export User"))
                .andExpect(jsonPath("$.bodyMetrics").isArray());
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

    @Test
    void internalRelayRejectsMissingAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/internal/outbox/publish")
                        .param("batchSize", "10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void goalReconcileRejectsOrdinaryUserRole() throws Exception {
        mockMvc.perform(post("/api/v1/internal/goals/reconcile")
                        .with(user("plain-user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void goalReconcileRejectsMissingAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/internal/goals/reconcile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminGoalReconcileRepublishesEveryActiveGoal() throws Exception {
        String token = tokenFor("reconcile@example.com", "Reconcile User");
        mockMvc.perform(put("/api/v1/users/me/goals/WEIGHT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "targetValue": 70.0,
                                  "targetUnit": "kg",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isOk());
        int activeGoals = userGoalRepository.findByStatusOrderByUserIdAscGoalTypeAsc(GoalStatus.ACTIVE).size();

        mockMvc.perform(post("/api/v1/internal/goals/reconcile")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedCount").value(activeGoals))
                .andExpect(jsonPath("$.usersTouched").value(
                        userGoalRepository.findByStatusOrderByUserIdAscGoalTypeAsc(GoalStatus.ACTIVE).stream()
                                .map(goal -> goal.getUserId()).distinct().count()));
        assertTrue(activeGoals >= 1);
    }

    @Test
    void profileEventsNeverCarryGenderOrBirthDateAndReferenceEventCarriesDerivedValues() throws Exception {
        String token = tokenFor("gender-event@example.com", "Gender Event");
        mockMvc.perform(put("/api/v1/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "displayName": "Gender Event",
                                  "birthDate": "1990-05-20",
                                  "gender": "FEMALE",
                                  "heightCm": 168.0,
                                  "onboardingCompleted": true
                                }
                                """))
                .andExpect(status().isOk());

        var all = outboxEventRepository.findAll();
        for (var event : all) {
            assertFalse(event.getPayload().contains("1990-05-20"));
            assertFalse(event.getPayload().contains("\"gender\":"));
            assertFalse(event.getPayload().contains("\"birthDate\":"));
        }
        var events = all.stream()
                .filter(event -> "NutritionReferenceProfileUpdated".equals(event.getEventType()))
                .toList();
        assertEquals(1, events.size());
        assertTrue(events.get(0).getPayload().contains("\"referenceSex\":\"FEMALE\""));
        assertTrue(events.get(0).getPayload().contains("\"ageBand\":"));
    }

    @Test
    void referenceRangePreferenceDefaultsToProfileAndPublishesWhenChanged() throws Exception {
        String token = tokenFor("range-pref@example.com", "Range Pref");
        mockMvc.perform(put("/api/v1/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {"displayName": "Range Pref", "gender": "MALE", "onboardingCompleted": true}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/me/preferences").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nutrition.nutrientReferenceRange").value("PROFILE"));
        outboxEventRepository.deleteAll();

        mockMvc.perform(put("/api/v1/users/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(preferencesJson("\"nutrientReferenceRange\": \"FEMALE\", ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nutrition.nutrientReferenceRange").value("FEMALE"));

        var reference = outboxEventRepository.findAll().stream()
                .filter(event -> "NutritionReferenceProfileUpdated".equals(event.getEventType()))
                .toList();
        assertEquals(1, reference.size());
        assertTrue(reference.get(0).getPayload().contains("\"referenceSex\":\"FEMALE\""));

        outboxEventRepository.deleteAll();
        mockMvc.perform(put("/api/v1/users/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(preferencesJson("")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nutrition.nutrientReferenceRange").value("FEMALE"));
        assertTrue(outboxEventRepository.findAll().stream()
                .noneMatch(event -> "NutritionReferenceProfileUpdated".equals(event.getEventType())));
    }

    @Test
    void referenceRangePreferenceRejectsUnknownValue() throws Exception {
        String token = tokenFor("range-bad@example.com", "Range Bad");
        mockMvc.perform(put("/api/v1/users/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(preferencesJson("\"nutrientReferenceRange\": \"OTHER\", ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void personFactsReconcileRejectsOrdinaryUserRole() throws Exception {
        mockMvc.perform(post("/api/v1/internal/profiles/reconcile")
                        .with(user("plain-user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void personFactsReconcileRejectsMissingAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/internal/profiles/reconcile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminPersonFactsReconcileRepublishesReferenceProfilePreferencesAndLatestWeight() throws Exception {
        UUID factsUserId = UUID.randomUUID();
        String token = tokenFor(factsUserId, "facts@example.com", "Facts User");
        mockMvc.perform(put("/api/v1/users/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "displayName": "Facts User",
                                  "birthDate": "1988-02-01",
                                  "gender": "MALE",
                                  "heightCm": 180.0,
                                  "onboardingCompleted": true
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/users/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "app": {"preferredUnits": "100002", "language": "110002", "timezone": "Asia/Singapore",
                                          "privacyLevel": "120002", "dataSharingConsent": true},
                                  "training": {"fitnessLevel": "200002", "trainingLocation": "210002", "weeklyWorkoutDays": 3,
                                               "preferredDurationMinutes": 45, "preferredWorkoutTime": "19:30:00",
                                               "intensityPreference": "220003", "equipmentInventory": ["230002"],
                                               "focusAreas": ["240001"]},
                                  "nutrition": {"dietaryPattern": "300003", "mealFrequency": 3,
                                                "calorieTrackingMode": "310003", "macroPreference": "320002",
                                                "waterGoalMl": 2500},
                                  "notifications": {"pushEnabled": true, "emailEnabled": true, "workoutReminderEnabled": true,
                                                    "mealReminderEnabled": true, "weighInReminderEnabled": true,
                                                    "goalProgressReminderEnabled": true, "weeklyProgressReportEnabled": true,
                                                    "weeklyProgressReportChannel": "EMAIL", "marketingEmailsEnabled": false,
                                                    "quietHoursStart": "22:00:00", "quietHoursEnd": "07:00:00"}
                                }
                                """))
                .andExpect(status().isOk());
        for (String weight : new String[] {"82.0", "80.5"}) {
            mockMvc.perform(post("/api/v1/users/me/body-metrics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + token)
                            .content("""
                                    {"weightKg": %s, "measuredAt": "%s", "source": "MANUAL"}
                                    """.formatted(weight, "82.0".equals(weight) ? "2026-06-10T08:00:00Z" : "2026-06-11T08:00:00Z")))
                    .andExpect(status().isCreated());
        }
        outboxEventRepository.deleteAll();

        mockMvc.perform(post("/api/v1/internal/profiles/reconcile")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceProfilesPublished").value(userProfileRepository.count()))
                .andExpect(jsonPath("$.preferencesPublished").isNumber())
                .andExpect(jsonPath("$.bodyMetricsPublished").isNumber())
                .andExpect(jsonPath("$.energyProfilesPublished").isNumber());

        var events = outboxEventRepository.findAll();
        assertTrue(events.stream().anyMatch(event -> "NutritionReferenceProfileUpdated".equals(event.getEventType())
                && factsUserId.equals(event.getUserId())
                && event.getPayload().contains("\"referenceSex\":\"MALE\"")));
        assertTrue(events.stream().noneMatch(event -> "UserProfileUpdated".equals(event.getEventType())));
        assertTrue(events.stream().anyMatch(event -> "UserPreferencesUpdated".equals(event.getEventType())));
        // The water goal rides on the republished preferences so nutrition can project it.
        assertTrue(events.stream().anyMatch(event -> "UserPreferencesUpdated".equals(event.getEventType())
                && factsUserId.equals(event.getUserId())
                && event.getPayload().contains("\"waterGoalMl\":2500")));
        var weights = events.stream()
                .filter(event -> "BodyMetricsRecorded".equals(event.getEventType()))
                .filter(event -> factsUserId.equals(event.getUserId()))
                .toList();
        assertEquals(1, weights.size());
        assertTrue(weights.get(0).getPayload().contains("80.5"));
        assertTrue(events.stream().anyMatch(event -> "EnergyProfileComputed".equals(event.getEventType())
                && factsUserId.equals(event.getUserId())
                && event.getPayload().contains("\"estimated\":false")));
    }

    private static String preferencesJson(String nutritionExtra) {
        return """
                {
                  "app": {"preferredUnits": "100002", "language": "110002", "timezone": "Asia/Singapore",
                          "privacyLevel": "120002", "dataSharingConsent": true},
                  "training": {"fitnessLevel": "200002", "trainingLocation": "210002", "weeklyWorkoutDays": 3,
                               "preferredDurationMinutes": 45, "preferredWorkoutTime": "19:30:00",
                               "intensityPreference": "220003", "equipmentInventory": ["230002"],
                               "focusAreas": ["240001"]},
                  "nutrition": {%s"dietaryPattern": "300003", "mealFrequency": 3,
                                "calorieTrackingMode": "310003", "macroPreference": "320002",
                                "waterGoalMl": 2500},
                  "notifications": {"pushEnabled": true, "emailEnabled": true, "workoutReminderEnabled": true,
                                    "mealReminderEnabled": true, "weighInReminderEnabled": true,
                                    "goalProgressReminderEnabled": true, "weeklyProgressReportEnabled": true,
                                    "weeklyProgressReportChannel": "EMAIL", "marketingEmailsEnabled": false,
                                    "quietHoursStart": "22:00:00", "quietHoursEnd": "07:00:00"}
                }
                """.formatted(nutritionExtra);
    }

    private String tokenFor(String email, String displayName) {
        return tokenFor(UUID.randomUUID(), email, displayName);
    }

    private String tokenFor(UUID userId, String email, String displayName) {
        return Jwts.builder()
                .claims(Map.of(
                        "userId", userId.toString(),
                        "displayName", displayName,
                        "role", "USER"))
                .subject(email)
                .signWith(Keys.hmacShaKeyFor(
                        "test-secret-test-secret-test-secret-123456".getBytes(StandardCharsets.UTF_8)))
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
