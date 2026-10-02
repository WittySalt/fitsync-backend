package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One recorded cook. */
public record RecipeCookView(UUID cookId, UUID recipeId, BigDecimal servings, Instant startedAt, Instant finishedAt) {
}
