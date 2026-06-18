package com.fitsync.user.dto;

import java.time.LocalTime;

import com.fitsync.user.domain.NotificationChannel;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
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
        """)
public record UpsertNotificationPreferencesRequest(
        boolean pushEnabled,
        boolean emailEnabled,
        boolean workoutReminderEnabled,
        boolean mealReminderEnabled,
        boolean weighInReminderEnabled,
        boolean goalProgressReminderEnabled,
        Boolean weeklyProgressReportEnabled,
        NotificationChannel weeklyProgressReportChannel,
        boolean marketingEmailsEnabled,
        LocalTime quietHoursStart,
        LocalTime quietHoursEnd) {
}
