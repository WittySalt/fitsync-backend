package com.fitsync.contracts.events.user;

import java.time.Instant;

/**
 * EV-3 — the user's computed energy profile. user-service does the arithmetic from the
 * profile's age/sex/height/activity plus the latest weight and publishes ONLY the
 * results, so downstream consumers (recommendation/analytics) get precise targets
 * without the PII inputs ever leaving user-service.
 *
 * @param bmrCalories   Mifflin–St Jeor basal metabolic rate, kcal/day
 * @param tdeeCalories  BMR scaled by the profile's activity level, kcal/day
 * @param activityLevel the activity level the TDEE was computed with (nullable — the
 *                      default factor was used when the profile has none)
 * @param computedAt    when user-service computed this profile
 */
public record EnergyProfileComputedPayload(
        Integer bmrCalories,
        Integer tdeeCalories,
        String activityLevel,
        Instant computedAt) {
}
