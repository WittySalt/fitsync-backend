package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** Records that the caller cooked a recipe. finishedAt defaults to now and may not be in the future. */
public record CreateRecipeCookRequest(
        @NotNull UUID clientCookId,
        @NotNull @DecimalMin("0.25") @DecimalMax("50") BigDecimal servings,
        Instant startedAt,
        Instant finishedAt) {
}
