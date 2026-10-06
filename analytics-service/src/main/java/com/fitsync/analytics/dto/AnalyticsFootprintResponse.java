package com.fitsync.analytics.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * The Me page footprint (me-achievements-spec.md, sections 1 and 5): one cell per day from the Monday on
 * or before the member-since day to the Sunday of the current week, and three lifetime numbers.
 *
 * @param memberSince the account's first day when the caller passed it, else null (analytics holds no
 *                    account creation date, so the client takes it from the profile)
 * @param from        the Monday the grid starts on
 * @param to          the Sunday of the current week in the user's zone
 * @param days        every day from from to to, oldest first
 */
public record AnalyticsFootprintResponse(
        LocalDate memberSince,
        LocalDate from,
        LocalDate to,
        List<Day> days,
        Totals totals) {

    public AnalyticsFootprintResponse {
        days = List.copyOf(days);
    }

    @Override
    public List<Day> days() {
        return List.copyOf(days);
    }

    /**
     * @param trained a session was completed that day
     * @param logged  a meal was logged that day; a fast day counts
     */
    public record Day(LocalDate date, boolean trained, boolean logged) {
    }

    /**
     * Lifetime numbers.
     *
     * @param workouts   completed sessions
     * @param daysLogged days with a meal logged, fast days included
     * @param badges     catalogue badges earned
     */
    public record Totals(int workouts, int daysLogged, int badges) {
    }
}
