package com.fitsync.contracts.events.user;

public record UserPreferencesUpdatedPayload(
        String preferredUnits,
        Integer workoutDaysPerWeek,
        String dietaryPreference,
        boolean notificationsEnabled) {
}
