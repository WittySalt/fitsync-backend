package com.fitsync.analytics.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import com.fitsync.web.error.BadRequestException;

/**
 * The period of a Progress page (contract section 0): {@code from} and {@code to} are both included, the
 * period is at most 370 days long, and {@code previous} is the period of the same length right before it.
 * Weeks run Monday to Sunday.
 */
public record ProgressWindow(LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo) {

    public static final int MAX_DAYS = 370;
    /**
     * The week that holds today enters a weekly series, a head or a highlight only once it has this many
     * logged days before today; today itself never counts (progress review fixes, section 1).
     */
    public static final int MIN_FULL_DAYS_FOR_CURRENT_WEEK = 2;

    public static ProgressWindow of(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BadRequestException("from and to are required");
        }
        if (from.isAfter(to)) {
            throw new BadRequestException("from must not be after to");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_DAYS) {
            throw new BadRequestException("The period must not exceed " + MAX_DAYS + " days");
        }
        return new ProgressWindow(from, to, from.minusDays(days), from.minusDays(1));
    }

    /** Days in the period, both ends included. */
    public int days() {
        return (int) ChronoUnit.DAYS.between(from, to) + 1;
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(from) && !date.isAfter(to);
    }

    public boolean containsPrevious(LocalDate date) {
        return !date.isBefore(previousFrom) && !date.isAfter(previousTo);
    }

    /** The Monday of the week the date falls in. */
    public static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** The Mondays of every week that touches the period, oldest first. */
    public List<LocalDate> weekStarts() {
        return weekStarts(from, to);
    }

    /** The Mondays of every week that touches the previous period, oldest first. */
    public List<LocalDate> previousWeekStarts() {
        return weekStarts(previousFrom, previousTo);
    }

    static List<LocalDate> weekStarts(LocalDate first, LocalDate last) {
        List<LocalDate> weeks = new ArrayList<>();
        for (LocalDate monday = weekStart(first); !monday.isAfter(last); monday = monday.plusWeeks(1)) {
            weeks.add(monday);
        }
        return weeks;
    }
}
