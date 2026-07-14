package com.fitsync.recommendation.domain;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Resolves the timezone snapshot stored on the context (a free string from the user
 * events) to a {@link ZoneId}, falling back to UTC for missing or malformed values.
 */
public final class UserTimeZones {

    private UserTimeZones() {
    }

    public static ZoneId resolve(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException ignored) {
            return ZoneOffset.UTC;
        }
    }
}
