package com.fitsync.analytics.dto;

import java.time.LocalDate;

public record AnalyticsStreaksResponse(
        int currentStreakDays,
        int bestStreakDays,
        LocalDate bestStreakFrom,
        LocalDate bestStreakTo,
        LocalDate lastActiveDate,
        String timezone) {
}
