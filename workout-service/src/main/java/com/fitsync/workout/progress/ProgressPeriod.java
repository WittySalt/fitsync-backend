package com.fitsync.workout.progress;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.fitsync.web.error.BadRequestException;
import com.fitsync.workout.dto.progress.PeriodRange;

/**
 * An inclusive period of local dates in the caller's zone, at most {@value #MAX_DAYS} days long.
 * {@link #previous()} is the period of the same length that ends the day before {@code from}.
 */
public record ProgressPeriod(LocalDate from, LocalDate to) {

    public static final int MAX_DAYS = 370;

    public ProgressPeriod {
        if (from == null || to == null) {
            throw new BadRequestException("PERIOD_REQUIRED", "Both from and to are required.");
        }
        if (from.isAfter(to)) {
            throw new BadRequestException("PERIOD_INVALID", "from must be on or before to.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new BadRequestException("PERIOD_TOO_LONG", "A period can be at most " + MAX_DAYS + " days.");
        }
    }

    public long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    public ProgressPeriod previous() {
        return new ProgressPeriod(from.minusDays(days()), from.minusDays(1));
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(from) && !date.isAfter(to);
    }

    public PeriodRange range() {
        return new PeriodRange(from, to);
    }

    /** The Monday of the week holding the given date. */
    public static LocalDate mondayOf(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - 1L);
    }
}
