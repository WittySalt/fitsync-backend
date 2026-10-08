package com.fitsync.analytics.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Shared rules for goal status: a goal expires the day after its target date, and the days-remaining
 * countdown never goes below zero. Callers pass "today" in the user's zone where they know it.
 */
public final class GoalExpiryPolicy {

    public static final String ACTIVE_STATUS = "ACTIVE";
    public static final String EXPIRED_STATUS = "EXPIRED";

    private GoalExpiryPolicy() {
    }

    public static boolean isActive(String status) {
        return ACTIVE_STATUS.equals(normalizeStatus(status));
    }

    public static boolean isExpired(LocalDate targetDate, LocalDate today) {
        return targetDate != null && targetDate.isBefore(today);
    }

    public static Integer daysRemaining(LocalDate targetDate, LocalDate today) {
        if (targetDate == null) {
            return null;
        }
        return Math.toIntExact(Math.max(ChronoUnit.DAYS.between(today, targetDate), 0));
    }

    public static String normalizeStatus(String status) {
        return status == null ? "" : status.toUpperCase(Locale.ROOT);
    }
}
