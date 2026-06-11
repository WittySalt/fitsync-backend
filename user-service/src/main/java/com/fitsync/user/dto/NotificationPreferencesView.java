package com.fitsync.user.dto;

import java.time.LocalTime;

public record NotificationPreferencesView(
        boolean pushEnabled,
        boolean emailEnabled,
        boolean workoutReminderEnabled,
        boolean mealReminderEnabled,
        boolean weighInReminderEnabled,
        boolean goalProgressReminderEnabled,
        boolean marketingEmailsEnabled,
        LocalTime quietHoursStart,
        LocalTime quietHoursEnd) {
}
