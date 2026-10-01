package com.fitsync.nutrition.dto;

import java.time.Instant;
import java.util.UUID;

/** A logged drink that counts toward the day's water, with the water it holds in millilitres. */
public record DrinkWaterEntryView(
        UUID mealItemId,
        UUID mealId,
        String foodName,
        Integer waterMl,
        Instant consumedAt) {
}
