package com.fitsync.nutrition.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * Copy every meal of the source day (path) to {@code targetDate}, preserving each meal's
 * time-of-day in the client's zone (the X-Timezone header, UTC when absent).
 */
public record CopyDayRequest(
        @NotNull LocalDate targetDate) {
}
