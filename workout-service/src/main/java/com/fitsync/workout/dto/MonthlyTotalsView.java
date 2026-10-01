package com.fitsync.workout.dto;

/** The headline numbers of one month. */
public record MonthlyTotalsView(int sessions, int minutes, int records, int longestStreakDays) {
}
