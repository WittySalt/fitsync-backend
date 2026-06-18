package com.fitsync.analytics.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public final class GoalExpiryPolicy {

    public static final String ACTIVE_STATUS = "ACTIVE";
    public static final String EXPIRED_STATUS = "EXPIRED";

    private GoalExpiryPolicy() {
    }

    public static boolean isActive(String status) {
        return ACTIVE_STATUS.equals(normalizeStatus(status));
    }

    public static boolean isExpired(LocalDate targetDate, LocalDate todayUtc) {
        return targetDate != null && targetDate.isBefore(todayUtc);
    }

    public static Integer daysRemaining(LocalDate targetDate, LocalDate todayUtc) {
        if (targetDate == null) {
            return null;
        }
        return Math.toIntExact(Math.max(ChronoUnit.DAYS.between(todayUtc, targetDate), 0));
    }

    public static String normalizeStatus(String status) {
        return status == null ? "" : status.toUpperCase(Locale.ROOT);
    }
}
