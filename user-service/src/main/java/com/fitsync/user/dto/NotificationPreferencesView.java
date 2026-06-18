package com.fitsync.user.dto;

import java.time.LocalTime;

import com.fitsync.user.domain.NotificationChannel;

public record NotificationPreferencesView(
        boolean pushEnabled,
        boolean emailEnabled,
        boolean workoutReminderEnabled,
        boolean mealReminderEnabled,
        boolean weighInReminderEnabled,
        boolean goalProgressReminderEnabled,
        boolean weeklyProgressReportEnabled,
        NotificationChannel weeklyProgressReportChannel,
        boolean marketingEmailsEnabled,
        LocalTime quietHoursStart,
        LocalTime quietHoursEnd) {
}
