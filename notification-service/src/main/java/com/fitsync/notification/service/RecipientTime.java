package com.fitsync.notification.service;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.fitsync.notification.entity.RecipientEntity;

/**
 * Local-time rules shared by the planner and the dispatcher: which timezone a user lives in and
 * whether a moment falls inside their quiet hours.
 */
public final class RecipientTime {

    /** The zone used when a profile has no timezone or one Java does not know, as in the app. */
    public static final ZoneId FALLBACK_ZONE = ZoneId.of("Asia/Singapore");

    private RecipientTime() {
    }

    public static ZoneId zoneOf(RecipientEntity recipient) {
        return zoneOf(recipient == null ? null : recipient.getTimezone());
    }

    public static ZoneId zoneOf(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return FALLBACK_ZONE;
        }
        try {
            return ZoneId.of(timezone.strip());
        } catch (DateTimeException ex) {
            return FALLBACK_ZONE;
        }
    }

    /**
     * True when the local time lies inside quiet hours. A window whose start is later than its end
     * runs across midnight (22:00 to 07:00). A missing bound or an empty window means no quiet hours.
     */
    public static boolean inQuietHours(LocalTime time, LocalTime start, LocalTime end) {
        if (start == null || end == null || start.equals(end)) {
            return false;
        }
        if (start.isBefore(end)) {
            return !time.isBefore(start) && time.isBefore(end);
        }
        return !time.isBefore(start) || time.isBefore(end);
    }

    /** The first moment after {@code now} at which the local clock shows the end of quiet hours. */
    public static Instant quietHoursEnd(ZonedDateTime now, LocalTime end) {
        ZonedDateTime candidate = now.toLocalDate().atTime(end).atZone(now.getZone());
        if (!candidate.isAfter(now)) {
            candidate = now.toLocalDate().plusDays(1).atTime(end).atZone(now.getZone());
        }
        return candidate.toInstant();
    }

    /** True when the user's last workout falls on the given local date. */
    public static boolean workedOutOn(RecipientEntity recipient, LocalDate localDate, ZoneId zone) {
        Instant last = recipient.getLastWorkoutAt();
        return last != null && last.atZone(zone).toLocalDate().equals(localDate);
    }
}
