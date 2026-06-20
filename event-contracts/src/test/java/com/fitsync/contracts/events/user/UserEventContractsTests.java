package com.fitsync.contracts.events.user;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserEventContractsTests {

    @Test
    void shouldExposeUserPreferencesUpdatedPayloadFields() {
        UserPreferencesUpdatedPayload payload = new UserPreferencesUpdatedPayload(
                "100001",
                4,
                "300001",
                true,
                List.of("230002", "230004"),
                List.of("240001", "240005"));

        assertEquals("100001", payload.preferredUnits());
        assertEquals(4, payload.workoutDaysPerWeek());
        assertEquals("300001", payload.dietaryPreference());
        assertEquals(true, payload.notificationsEnabled());
        assertEquals(List.of("230002", "230004"), payload.equipmentInventory());
        assertEquals(List.of("240001", "240005"), payload.focusAreas());
    }
}
