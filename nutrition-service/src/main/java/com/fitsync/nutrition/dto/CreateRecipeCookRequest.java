package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Records that the caller cooked a recipe. finishedAt defaults to now and may not be in the future.
 * servingsLogged is what the Finished card logged as a meal; the whole servings that are left over are
 * {@code servings - servingsLogged} rounded down. Left out, the cook has no leftovers (a client from before
 * leftovers existed); an explicit 0 leaves every serving.
 */
public record CreateRecipeCookRequest(
        @NotNull UUID clientCookId,
        @NotNull @DecimalMin("0.25") @DecimalMax("50") BigDecimal servings,
        Instant startedAt,
        Instant finishedAt,
        @DecimalMin("0") @DecimalMax("50") BigDecimal servingsLogged) {

    public CreateRecipeCookRequest(UUID clientCookId, BigDecimal servings, Instant startedAt, Instant finishedAt) {
        this(clientCookId, servings, startedAt, finishedAt, null);
    }
}
