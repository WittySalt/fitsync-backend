package com.fitsync.workout.dto;

import java.time.LocalDate;

/** A Monday-to-Sunday week that overlaps the month. The dates are the whole week's, while the
 * counts only cover the days inside the month. */
public record MonthlyWeekView(
        LocalDate weekStart,
        LocalDate weekEnd,
        int sessions,
        int minutes,
        int strengthSessions,
        int cardioSessions,
        int mobilitySessions,
        int sportSessions,
        boolean complete) {
}
