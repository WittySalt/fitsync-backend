package com.fitsync.workout.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Shared timezone-resolution and week-boundary arithmetic for the training insight endpoints
 * ({@code /insights/weekly} and {@code /insights/training}). Kept as small, dependency-free static
 * helpers so both services can share behaviour without either depending on the other.
 */
final class InsightDates {

    private InsightDates() {
    }

    /** Resolves the caller-supplied IANA zone id, defaulting to UTC; never returns null. */
    static ZoneId resolveZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            // The value is left out of the message, so the response never repeats what was sent.
            throw new IllegalArgumentException("The timezone is not a recognised zone id.");
        }
    }

    /** The Monday of the week containing the given date. */
    static LocalDate mondayOf(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - 1L);
    }
}
