package com.fitsync.nutrition.dto;

import java.time.Instant;
import java.util.UUID;

/** A cook of the last 7 days with servings still left; kcalPerServing is null when it cannot be worked out. */
public record CookLeftoverView(
        UUID cookId,
        UUID recipeId,
        String name,
        String imageUrl,
        Instant cookedAt,
        int servingsLeft,
        Integer kcalPerServing) {
}
