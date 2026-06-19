package com.fitsync.analytics.dto;

import java.time.LocalDate;
import java.util.List;

public record AnalyticsActivityCalendarResponse(
        LocalDate from,
        LocalDate to,
        List<ActivityCalendarDay> days) {

    public AnalyticsActivityCalendarResponse {
        days = List.copyOf(days);
    }

    @Override
    public List<ActivityCalendarDay> days() {
        return List.copyOf(days);
    }

    public record ActivityCalendarDay(
            LocalDate date,
            int workoutCount,
            int workoutMinutes) {
    }
}
