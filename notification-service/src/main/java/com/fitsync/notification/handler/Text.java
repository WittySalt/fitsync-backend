package com.fitsync.notification.handler;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Small clean-ups applied to event values before they are stored. */
final class Text {

    private Text() {
    }

    /** Trims, turns blank into null and cuts to the column width, so a long value never fails a write. */
    static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    /** Enum-like values are compared in upper case, whatever case the publisher used. */
    static String upper(String value, int max) {
        String clipped = clip(value, max);
        return clipped == null ? null : clipped.toUpperCase(Locale.ROOT);
    }

    /** Reads a local "HH:mm" time; a missing or malformed value means the user has not set one. */
    static LocalTime time(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.strip());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
