package com.fitsync.contracts.events.user;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
                List.of("240001", "240005"),
                "Asia/Singapore");

        assertEquals("100001", payload.preferredUnits());
        assertEquals(4, payload.workoutDaysPerWeek());
        assertEquals("300001", payload.dietaryPreference());
        assertEquals(true, payload.notificationsEnabled());
        assertEquals(List.of("230002", "230004"), payload.equipmentInventory());
        assertEquals(List.of("240001", "240005"), payload.focusAreas());
        assertEquals("Asia/Singapore", payload.timezone());
    }

    @Test
    void shouldExposeUserProfileUpdatedPayloadTimezone() {
        UserProfileUpdatedPayload payload = new UserProfileUpdatedPayload(
                "Alice",
                new BigDecimal("170.50"),
                "MODERATELY_ACTIVE",
                true,
                List.of("timezone"),
                "Asia/Singapore");

        assertEquals("Alice", payload.displayName());
        assertEquals(new BigDecimal("170.50"), payload.heightCm());
        assertEquals("MODERATELY_ACTIVE", payload.activityLevel());
        assertEquals(true, payload.onboardingCompleted());
        assertEquals(List.of("timezone"), payload.updatedFields());
        assertEquals("Asia/Singapore", payload.timezone());
    }

    @Test
    void shouldCarryGenderAndBirthDateAndDefaultThemToNullForOlderCallers() {
        UserProfileUpdatedPayload full = new UserProfileUpdatedPayload(
                "Alice", new BigDecimal("170.50"), "MODERATELY_ACTIVE", true, List.of("gender"),
                "Asia/Singapore", "FEMALE", LocalDate.parse("1995-04-12"));
        UserProfileUpdatedPayload older = new UserProfileUpdatedPayload(
                "Alice", new BigDecimal("170.50"), "MODERATELY_ACTIVE", true, List.of("timezone"),
                "Asia/Singapore");
        UserProfileUpdatedPayload oldest = new UserProfileUpdatedPayload(
                "Alice", new BigDecimal("170.50"), "MODERATELY_ACTIVE", true, List.of("timezone"));

        assertEquals("FEMALE", full.gender());
        assertEquals(LocalDate.parse("1995-04-12"), full.birthDate());
        assertEquals(null, older.gender());
        assertEquals(null, older.birthDate());
        assertEquals(null, oldest.timezone());
        assertEquals(null, oldest.birthDate());
    }

    @Test
    void shouldExposeAccountDeletedPayloadFields() {
        AccountDeletedPayload payload = new AccountDeletedPayload(
                Instant.parse("2026-06-25T04:00:00Z"),
                "USER_REQUESTED");

        assertEquals(Instant.parse("2026-06-25T04:00:00Z"), payload.deletedAt());
        assertEquals("USER_REQUESTED", payload.reason());
    }
}
