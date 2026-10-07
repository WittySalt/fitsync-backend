package com.fitsync.nutrition.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Reports how many servings were logged as a meal after the cook was recorded. The leftovers become
 * {@code servings - servingsLogged} rounded down, never below 0, so repeating a request changes nothing.
 */
public record UpdateRecipeCookRequest(
        @NotNull @DecimalMin("0") @DecimalMax("50") BigDecimal servingsLogged) {
}
