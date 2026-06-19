package com.fitsync.nutrition.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Copy every meal of the source day (path) to {@code targetDate}, preserving each meal's
 * time-of-day in {@code timezone} (defaults to UTC when omitted).
 */
public record CopyDayRequest(
        @NotNull LocalDate targetDate,
        @Size(max = 80) String timezone) {
}
