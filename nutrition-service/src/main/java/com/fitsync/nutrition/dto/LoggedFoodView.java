package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A previously logged food, surfaced for one-tap re-logging (recent / frequent foods).
 * Carries the last-used serving + macro snapshot so the caller can rebuild a
 * {@link MealItemInput} without another catalog lookup, plus how many times it has been logged
 * within the recent scan window.
 */
public record LoggedFoodView(
        UUID customFoodId,
        UUID foodCatalogItemId,
        UUID foodServingId,
        String externalFoodRef,
        String barcode,
        String foodName,
        String brandName,
        BigDecimal quantity,
        String unit,
        BigDecimal servingSize,
        String servingUnit,
        String snapshotServingLabel,
        Integer calories,
        BigDecimal proteinGrams,
        BigDecimal carbsGrams,
        BigDecimal fatGrams,
        BigDecimal fiberGrams,
        BigDecimal sugarGrams,
        BigDecimal sodiumMg,
        long timesLogged,
        Instant lastLoggedAt) {
}
