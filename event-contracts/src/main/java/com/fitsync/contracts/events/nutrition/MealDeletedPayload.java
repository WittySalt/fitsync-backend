package com.fitsync.contracts.events.nutrition;

import java.time.Instant;
import java.util.UUID;

public record MealDeletedPayload(
        UUID mealId,
        Instant deletedAt) {
}
