package com.fitsync.workout.service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;

/**
 * Works out the time of day a person usually starts training from their session start instants.
 * The result is the median local start time rounded to 30 minutes, and only when enough sessions
 * exist and at least half of them start close to that median.
 */
final class UsualStartTime {

    static final int MIN_SESSIONS = 3;
    static final int ROUND_TO_MINUTES = 30;
    static final int AGREEMENT_WINDOW_MINUTES = 90;
    private static final int MINUTES_PER_DAY = 24 * 60;

    private UsualStartTime() {
    }

    /** The usual start as "HH:mm" (24-hour), or null when there is no usual time. */
    static String compute(Collection<Instant> starts, ZoneId zone) {
        if (starts.size() < MIN_SESSIONS) {
            return null;
        }
        List<Integer> minutes = starts.stream()
                .map(start -> start.atZone(zone).toLocalTime())
                .map(time -> time.getHour() * 60 + time.getMinute())
                .sorted()
                .toList();
        // For an even count this is the lower of the two middle values.
        int median = minutes.get((minutes.size() - 1) / 2);
        long close = minutes.stream()
                .filter(value -> Math.abs(value - median) <= AGREEMENT_WINDOW_MINUTES)
                .count();
        if (close * 2 < minutes.size()) {
            return null;
        }
        int rounded = ((median + ROUND_TO_MINUTES / 2) / ROUND_TO_MINUTES) * ROUND_TO_MINUTES;
        rounded %= MINUTES_PER_DAY;
        return String.format("%02d:%02d", rounded / 60, rounded % 60);
    }
}
