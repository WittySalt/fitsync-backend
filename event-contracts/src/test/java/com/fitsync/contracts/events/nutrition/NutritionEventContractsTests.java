package com.fitsync.contracts.events.nutrition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NutritionEventContractsTests {

    @Test
    void shouldExposeMealLoggedPayloadFields() {
        UUID mealId = UUID.randomUUID();
        Instant consumedAt = Instant.parse("2026-06-11T12:45:00Z");

        MealLoggedPayload payload = new MealLoggedPayload(
                mealId,
                "DINNER",
                consumedAt,
                620,
                new BigDecimal("42.0"),
                new BigDecimal("55.0"),
                new BigDecimal("21.0"),
                3,
                "MANUAL");

        assertEquals(mealId, payload.mealId());
        assertEquals("DINNER", payload.mealType());
        assertEquals(consumedAt, payload.consumedAt());
        assertEquals(620, payload.totalCalories());
        assertEquals(new BigDecimal("42.0"), payload.totalProteinGrams());
        assertEquals(new BigDecimal("55.0"), payload.totalCarbsGrams());
        assertEquals(new BigDecimal("21.0"), payload.totalFatGrams());
        assertEquals(3, payload.itemCount());
        assertEquals("MANUAL", payload.source());
        assertEquals(java.util.Map.of(), payload.foodGroupCalories());
    }

    @Test
    void shouldExposeMealLoggedFoodGroupBreakdown() {
        MealLoggedPayload payload = new MealLoggedPayload(
                UUID.randomUUID(),
                "LUNCH",
                Instant.parse("2026-06-11T05:00:00Z"),
                640,
                new BigDecimal("40.0"),
                new BigDecimal("60.0"),
                new BigDecimal("20.0"),
                2,
                "MANUAL",
                java.util.Map.of("MEAT_POULTRY", 400, "VEGETABLES", 60));

        assertEquals(400, payload.foodGroupCalories().get("MEAT_POULTRY"));
        assertEquals(60, payload.foodGroupCalories().get("VEGETABLES"));
    }

    @Test
    void shouldExposeMealUpdatedPayloadFields() {
        UUID mealId = UUID.randomUUID();
        Instant consumedAt = Instant.parse("2026-06-11T18:10:00Z");

        MealUpdatedPayload payload = new MealUpdatedPayload(
                mealId,
                "SNACK",
                consumedAt,
                280,
                new BigDecimal("18.5"),
                new BigDecimal("24.0"),
                new BigDecimal("9.0"),
                2,
                "CUSTOM_FOOD");

        assertEquals(mealId, payload.mealId());
        assertEquals("SNACK", payload.mealType());
        assertEquals(consumedAt, payload.consumedAt());
        assertEquals(280, payload.totalCalories());
        assertEquals(new BigDecimal("18.5"), payload.totalProteinGrams());
        assertEquals(new BigDecimal("24.0"), payload.totalCarbsGrams());
        assertEquals(new BigDecimal("9.0"), payload.totalFatGrams());
        assertEquals(2, payload.itemCount());
        assertEquals("CUSTOM_FOOD", payload.source());
    }

    @Test
    void shouldExposeMealDeletedPayloadFields() {
        UUID mealId = UUID.randomUUID();
        Instant deletedAt = Instant.parse("2026-06-11T20:00:00Z");

        MealDeletedPayload payload = new MealDeletedPayload(mealId, deletedAt);

        assertEquals(mealId, payload.mealId());
        assertEquals(deletedAt, payload.deletedAt());
    }
}
