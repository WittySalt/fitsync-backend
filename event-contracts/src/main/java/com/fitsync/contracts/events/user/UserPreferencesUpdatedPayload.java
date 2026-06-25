package com.fitsync.contracts.events.user;

import java.util.List;

public record UserPreferencesUpdatedPayload(
        String preferredUnits,
        Integer workoutDaysPerWeek,
        String dietaryPreference,
        boolean notificationsEnabled,
        List<String> equipmentInventory,
        List<String> focusAreas,
        String timezone) {

    public UserPreferencesUpdatedPayload(String preferredUnits,
                                         Integer workoutDaysPerWeek,
                                         String dietaryPreference,
                                         boolean notificationsEnabled,
                                         List<String> equipmentInventory,
                                         List<String> focusAreas) {
        this(preferredUnits, workoutDaysPerWeek, dietaryPreference, notificationsEnabled,
                equipmentInventory, focusAreas, null);
    }

    public UserPreferencesUpdatedPayload {
        equipmentInventory = equipmentInventory == null ? List.of() : List.copyOf(equipmentInventory);
        focusAreas = focusAreas == null ? List.of() : List.copyOf(focusAreas);
    }
}
