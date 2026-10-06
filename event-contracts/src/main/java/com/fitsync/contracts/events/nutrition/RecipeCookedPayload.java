package com.fitsync.contracts.events.nutrition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A recipe was cooked. Published by nutrition-service when a cook is recorded, keyed by the cook id. A
 * retry of the same cook is answered with the first record and publishes nothing, so one cook is one event.
 */
public record RecipeCookedPayload(
        UUID cookId,
        UUID recipeId,
        BigDecimal servings,
        Instant finishedAt) {
}
