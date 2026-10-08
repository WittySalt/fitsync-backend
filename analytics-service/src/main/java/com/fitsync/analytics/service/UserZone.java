package com.fitsync.analytics.service;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Turns the timezone stored on a user's dashboard snapshot into a zone. The projections, the rebuild and
 * the reads all decide which local day an instant falls on with it, so they must agree on what an absent
 * or unreadable value means: UTC, rather than a failure, because a bad stored value must not stop events
 * from being projected or the dashboard from loading.
 */
public final class UserZone {

    private UserZone() {
    }

    /** The stored IANA zone, or UTC when none is stored or the stored value is not a valid zone. */
    public static ZoneId of(String storedTimezone) {
        if (storedTimezone == null || storedTimezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(storedTimezone.trim());
        } catch (DateTimeException ex) {
            return ZoneOffset.UTC;
        }
    }
}
