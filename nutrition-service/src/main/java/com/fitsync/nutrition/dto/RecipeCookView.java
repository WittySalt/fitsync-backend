package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One recorded cook; servingsLeft is the whole servings still uneaten (0 for none). */
public record RecipeCookView(UUID cookId, UUID recipeId, BigDecimal servings, Instant startedAt, Instant finishedAt,
                             int servingsLeft) {
}
