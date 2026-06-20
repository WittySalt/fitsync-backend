package com.fitsync.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "User preferences use code-stable enum values for configurable code tables.",
        example = """
        {
          "app": {
            "preferredUnits": "100001",
            "language": "110001",
            "timezone": "Asia/Singapore",
            "privacyLevel": "120002",
            "dataSharingConsent": true
          },
          "training": {
            "fitnessLevel": "200002",
            "trainingLocation": "210002",
            "weeklyWorkoutDays": 4,
            "preferredDurationMinutes": 60,
            "preferredWorkoutTime": "19:30:00",
            "intensityPreference": "220002",
            "equipmentInventory": ["230002", "230004"],
            "focusAreas": ["240001", "240005"]
          },
          "nutrition": {
            "dietaryPattern": "300001",
            "mealFrequency": 4,
            "calorieTrackingMode": "310002",
            "macroPreference": "320002",
            "waterGoalMl": 2500
          },
          "notifications": {
            "pushEnabled": true,
            "emailEnabled": false,
            "workoutReminderEnabled": true,
            "mealReminderEnabled": true,
            "weighInReminderEnabled": true,
            "goalProgressReminderEnabled": true,
            "marketingEmailsEnabled": false,
            "quietHoursStart": "22:00:00",
            "quietHoursEnd": "07:00:00"
          }
        }
        """)
public record UpsertUserPreferencesRequest(
        @Valid @NotNull UpsertAppPreferencesRequest app,
        @Valid @NotNull UpsertTrainingPreferencesRequest training,
        @Valid @NotNull UpsertNutritionPreferencesRequest nutrition,
        @Valid @NotNull UpsertNotificationPreferencesRequest notifications) {
}
