package com.fitsync.contracts.events.user;

/**
 * Emitted by user-service when anything a notification depends on changes: the notification
 * toggles, quiet hours, the profile timezone or the preferred training time. It carries the whole
 * current state, so a consumer can replace what it holds. Times are local "HH:mm" strings and the
 * timezone an IANA id; each may be null when the user has not set it. The user is carried on the
 * {@code FitSyncDomainEvent} envelope.
 */
public record NotificationPreferencesUpdatedPayload(
        boolean emailEnabled,
        boolean workoutReminderEnabled,
        boolean goalProgressReminderEnabled,
        boolean weeklyProgressReportEnabled,
        String weeklyProgressReportChannel,
        String quietHoursStart,
        String quietHoursEnd,
        String timezone,
        String preferredWorkoutTime) {
}
