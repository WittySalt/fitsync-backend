package com.fitsync.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import com.fitsync.analytics.dto.ProgressGoalResponse.Keep;
import com.fitsync.analytics.dto.ProgressGoalResponse.KeepWeek;
import com.fitsync.analytics.dto.ProgressGoalResponse.Road;
import com.fitsync.analytics.dto.ProgressGoalResponse.Tick;

/**
 * The weight goal as a road (progress v5 spec section 4): where it started, where the weight is now, the
 * whole kilos in between with the day each was crossed or is expected, and one state that sums it up.
 *
 * <p>A kilo counts as crossed on the first day the mean of the weigh-ins of the 7 days up to that day passes
 * it in the goal's direction. The pace and the projected dates come from {@link WeightProjection}, so the
 * road agrees with the hero figures of the same page.
 */
final class ProgressRoad {

    static final String LOSE = "LOSE";
    static final String GAIN = "GAIN";
    static final String KEEP = "KEEP";
    static final String AHEAD = "AHEAD";
    static final String ON_COURSE = "ON_COURSE";
    static final String BEHIND = "BEHIND";
    static final String REACHED = "REACHED";
    static final String DATE_PASSED = "DATE_PASSED";
    static final String STALLED = "STALLED";
    static final String NO_PROJECTION = "NO_PROJECTION";

    /** A goal this close to the weight at the start is kept rather than lost or gained. */
    static final BigDecimal KEEP_BAND_KG = new BigDecimal("1.0");
    /** A weekly rate smaller than this (kg a week) over the last 28 days counts as standing still. */
    static final BigDecimal STALLED_BELOW = new BigDecimal("0.05");
    /** The projected date must be this many days from the goal date to count as ahead or behind. */
    static final int ON_COURSE_SLACK_DAYS = 7;
    static final int MEAN_DAYS = 7;
    static final int KEEP_WEEKS = 8;
    private static final BigDecimal DAYS_PER_WEEK = BigDecimal.valueOf(7);
    private static final BigDecimal ONE = BigDecimal.ONE;

    private ProgressRoad() {
    }

    /**
     * @param weights       every known weigh-in, one a date
     * @param goalCreatedOn the day the goal was set, null when unknown
     * @param goalKg        the weight goal
     * @param targetDate    the goal date, null when there is none
     * @param baselineKg    the weight the goal was set from, used only before the first weigh-in; may be null
     * @param reference     today, or the end of the period when that is earlier
     */
    static Road build(Map<LocalDate, BigDecimal> weights, LocalDate goalCreatedOn, BigDecimal goalKg,
                      LocalDate targetDate, BigDecimal baselineKg, LocalDate reference) {
        NavigableMap<LocalDate, BigDecimal> history = new TreeMap<>();
        weights.forEach((date, kg) -> {
            if (date != null && kg != null && !date.isAfter(reference)) {
                history.put(date, kg);
            }
        });
        if (history.isEmpty()) {
            String direction = baselineKg == null ? null : direction(baselineKg, goalKg);
            return new Road(direction, ProgressRoad.NO_PROJECTION, null, null, null, null, goalKg, targetDate, null,
                    null, null, null, List.of(), null);
        }

        Map.Entry<LocalDate, BigDecimal> first = goalCreatedOn == null ? null : history.ceilingEntry(goalCreatedOn);
        if (first == null) {
            first = history.firstEntry();
        }
        LocalDate startDate = first.getKey();
        BigDecimal startKg = first.getValue().setScale(1, RoundingMode.HALF_UP);
        NavigableMap<LocalDate, BigDecimal> sinceStart = history.tailMap(startDate, true);
        LocalDate nowDate = history.lastKey();
        BigDecimal nowKg = history.lastEntry().getValue().setScale(1, RoundingMode.HALF_UP);
        String direction = direction(startKg, goalKg);
        boolean keeping = KEEP.equals(direction);
        boolean enough = sinceStart.size() >= 2;

        WeightProjection.Result projection = WeightProjection.calculate(history, reference, goalKg);
        BigDecimal rate = projection.weeklyRateKg();
        List<DayMean> means = means(history, startDate, nowDate);

        Keep keep = keeping ? keep(history, goalKg, reference) : null;
        List<Tick> ticks = keeping ? List.of() : ticks(direction, startKg, nowKg, nowDate, goalKg, rate, means, enough);
        LocalDate reachedOn = keeping ? null : crossedOn(means, direction, goalKg);

        String state;
        if (!enough) {
            state = NO_PROJECTION;
        } else if (keeping) {
            state = keepState(means, goalKg, rate);
        } else if (reachedOn != null) {
            state = REACHED;
        } else if (targetDate != null && reference.isAfter(targetDate)) {
            state = DATE_PASSED;
        } else if (rate == null) {
            state = NO_PROJECTION;
        } else if (rate.abs().compareTo(STALLED_BELOW) < 0) {
            state = STALLED;
        } else {
            state = pace(projection.projectedDate(), targetDate);
        }

        BigDecimal fractionWeight;
        if (keeping) {
            fractionWeight = keep.weeks().isEmpty() ? BigDecimal.ZERO
                    : share(keep.weeks().stream().filter(KeepWeek::inside).count(), keep.weeks().size());
        } else if (REACHED.equals(state)) {
            fractionWeight = ONE.setScale(2);
        } else {
            BigDecimal span = startKg.subtract(goalKg).abs();
            BigDecimal travelled = LOSE.equals(direction) ? startKg.subtract(nowKg) : nowKg.subtract(startKg);
            fractionWeight = span.signum() == 0 ? BigDecimal.ZERO : clamp(travelled.divide(span, 2, RoundingMode.HALF_UP));
        }
        BigDecimal fractionTime = null;
        if (targetDate != null && targetDate.isAfter(startDate)) {
            fractionTime = clamp(BigDecimal.valueOf(ChronoUnit.DAYS.between(startDate, nowDate))
                    .divide(BigDecimal.valueOf(ChronoUnit.DAYS.between(startDate, targetDate)), 2, RoundingMode.HALF_UP));
        }
        return new Road(direction, state, startKg, startDate, nowKg, nowDate, goalKg, targetDate,
                nowKg.subtract(goalKg).abs().setScale(1, RoundingMode.HALF_UP), fractionWeight, fractionTime,
                keeping ? null : projection.projectedDate(), ticks, keep);
    }

    private static String direction(BigDecimal startKg, BigDecimal goalKg) {
        if (goalKg.subtract(startKg).abs().compareTo(KEEP_BAND_KG) <= 0) {
            return KEEP;
        }
        return goalKg.compareTo(startKg) < 0 ? LOSE : GAIN;
    }

    private static String pace(LocalDate projected, LocalDate targetDate) {
        if (projected == null) {
            return BEHIND;
        }
        if (targetDate == null) {
            return ON_COURSE;
        }
        long days = ChronoUnit.DAYS.between(targetDate, projected);
        if (days <= -ON_COURSE_SLACK_DAYS) {
            return AHEAD;
        }
        return days >= ON_COURSE_SLACK_DAYS ? BEHIND : ON_COURSE;
    }

    /** Inside the band now: holding steady when the pace is flat, else on course. Outside it: behind. */
    private static String keepState(List<DayMean> means, BigDecimal goalKg, BigDecimal rate) {
        BigDecimal latest = means.get(means.size() - 1).meanKg();
        if (latest.subtract(goalKg).abs().compareTo(KEEP_BAND_KG) > 0) {
            return BEHIND;
        }
        return rate == null || rate.abs().compareTo(STALLED_BELOW) < 0 ? STALLED : ON_COURSE;
    }

    private record DayMean(LocalDate date, BigDecimal meanKg) {
    }

    /** The mean of the weigh-ins of the 7 days up to each day from the start to now (days without one are left out). */
    private static List<DayMean> means(NavigableMap<LocalDate, BigDecimal> weights, LocalDate from, LocalDate to) {
        List<DayMean> means = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            NavigableMap<LocalDate, BigDecimal> window = weights.subMap(day.minusDays(MEAN_DAYS - 1L), true, day, true);
            if (window.isEmpty()) {
                continue;
            }
            BigDecimal sum = window.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            means.add(new DayMean(day, sum.divide(BigDecimal.valueOf(window.size()), 4, RoundingMode.HALF_UP)));
        }
        return means;
    }

    private static boolean passed(String direction, BigDecimal meanKg, BigDecimal threshold) {
        return LOSE.equals(direction) ? meanKg.compareTo(threshold) <= 0 : meanKg.compareTo(threshold) >= 0;
    }

    private static LocalDate crossedOn(List<DayMean> means, String direction, BigDecimal threshold) {
        for (DayMean mean : means) {
            if (passed(direction, mean.meanKg(), threshold)) {
                return mean.date();
            }
        }
        return null;
    }

    private static List<Tick> ticks(String direction, BigDecimal startKg, BigDecimal nowKg, LocalDate nowDate,
                                    BigDecimal goalKg, BigDecimal rate, List<DayMean> means, boolean dated) {
        boolean losing = LOSE.equals(direction);
        int from;
        int to;
        if (losing) {
            from = startKg.setScale(0, RoundingMode.CEILING).intValueExact() - 1;
            to = goalKg.setScale(0, RoundingMode.CEILING).intValueExact();
        } else {
            from = startKg.setScale(0, RoundingMode.FLOOR).intValueExact() + 1;
            to = goalKg.setScale(0, RoundingMode.FLOOR).intValueExact();
        }
        List<Tick> ticks = new ArrayList<>();
        for (int kg = from; losing ? kg >= to : kg <= to; kg += losing ? -1 : 1) {
            if (!dated) {
                ticks.add(new Tick(kg, null, null));
                continue;
            }
            BigDecimal threshold = BigDecimal.valueOf(kg);
            LocalDate crossed = crossedOn(means, direction, threshold);
            ticks.add(new Tick(kg, crossed, crossed == null ? projectedOn(direction, nowKg, nowDate, threshold, rate) : null));
        }
        return ticks;
    }

    /** The day a kilo is reached at the current pace, carried from the last weigh-in; null when it never is. */
    private static LocalDate projectedOn(String direction, BigDecimal nowKg, LocalDate nowDate, BigDecimal threshold,
                                         BigDecimal rate) {
        if (passed(direction, nowKg, threshold)) {
            return nowDate;
        }
        if (rate == null || rate.abs().compareTo(STALLED_BELOW) < 0) {
            return null;
        }
        boolean towards = LOSE.equals(direction) ? rate.signum() < 0 : rate.signum() > 0;
        if (!towards) {
            return null;
        }
        BigDecimal days = threshold.subtract(nowKg).abs().multiply(DAYS_PER_WEEK)
                .divide(rate.abs(), 0, RoundingMode.CEILING);
        if (days.compareTo(BigDecimal.valueOf(WeightProjection.MAX_PROJECTION_DAYS)) > 0) {
            return null;
        }
        return nowDate.plusDays(days.longValueExact());
    }

    private static Keep keep(NavigableMap<LocalDate, BigDecimal> history, BigDecimal goalKg, LocalDate reference) {
        LocalDate lastMonday = ProgressWindow.weekStart(reference);
        List<KeepWeek> weeks = new ArrayList<>();
        for (int i = KEEP_WEEKS - 1; i >= 0; i--) {
            LocalDate monday = lastMonday.minusWeeks(i);
            NavigableMap<LocalDate, BigDecimal> week = history.subMap(monday, true, monday.plusDays(6), true);
            if (week.isEmpty()) {
                continue;
            }
            BigDecimal average = week.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(week.size()), 2, RoundingMode.HALF_UP);
            weeks.add(new KeepWeek(monday, average, average.subtract(goalKg).abs().compareTo(KEEP_BAND_KG) <= 0));
        }
        return new Keep(KEEP_BAND_KG, weeks);
    }

    private static BigDecimal share(long part, int whole) {
        return BigDecimal.valueOf(part).divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal clamp(BigDecimal fraction) {
        if (fraction.signum() < 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return fraction.compareTo(ONE) > 0 ? ONE.setScale(2) : fraction;
    }
}
