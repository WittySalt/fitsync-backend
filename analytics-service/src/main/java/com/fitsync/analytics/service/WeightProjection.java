package com.fitsync.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * The weight projection of the Goal page. It reproduces user-service's ProgressInsightCalculator step for
 * step so the two pages give the same rate and date for the same weigh-ins: the slope of a least-squares
 * line through the daily weights of the last 28 days, per week, rounded to two decimals; no rate with fewer
 * than two weigh-ins or with less than seven days between the first and the last; the date counted from the
 * last weigh-in at that rounded pace, rounded up to whole days, left out when the pace leads away from the
 * goal, the goal is already reached, or it would take more than two years.
 *
 * <p>user-service reads raw weigh-ins and keeps the latest of each local date; here the daily body rows
 * already hold one weight a date, so the input is that map.
 */
public final class WeightProjection {

    public static final String METHOD = "28-day average change carried forward";
    static final int RATE_WINDOW_DAYS = 28;
    static final int MIN_RATE_SPAN_DAYS = 7;
    static final int MAX_PROJECTION_DAYS = 730;
    private static final BigDecimal DAYS_PER_WEEK = BigDecimal.valueOf(7);

    private WeightProjection() {
    }

    /**
     * @param latestKg     the newest weight up to the reference date, one decimal, null without any
     * @param measuredOn   the date of that weight
     * @param weeklyRateKg the 28-day rate in kg a week, null when it cannot be trusted
     * @param goalKg       the goal weight at two decimals, null without a goal
     * @param projectedDate the date the goal is reached at this pace, null as described above
     */
    public record Result(BigDecimal latestKg, LocalDate measuredOn, BigDecimal weeklyRateKg, BigDecimal goalKg,
                         LocalDate projectedDate) {
    }

    public static Result calculate(Map<LocalDate, BigDecimal> weightByDate, LocalDate referenceDate,
                                   BigDecimal goalKg) {
        NavigableMap<LocalDate, BigDecimal> weights = new TreeMap<>();
        weightByDate.forEach((date, weight) -> {
            if (date != null && weight != null && !date.isAfter(referenceDate)) {
                weights.put(date, weight);
            }
        });
        BigDecimal goal = goalKg == null ? null : goalKg.setScale(2, RoundingMode.HALF_UP);
        if (weights.isEmpty()) {
            return new Result(null, null, null, goal, null);
        }
        Map.Entry<LocalDate, BigDecimal> latest = weights.lastEntry();
        BigDecimal latestWeight = latest.getValue().setScale(1, RoundingMode.HALF_UP);
        BigDecimal rate = weeklyRate(weights, referenceDate);
        LocalDate projected = rate == null ? null : projectedDate(latest.getKey(), latestWeight, rate, goal);
        return new Result(latestWeight, latest.getKey(), rate, goal, projected);
    }

    private static BigDecimal weeklyRate(NavigableMap<LocalDate, BigDecimal> weightByDate, LocalDate referenceDate) {
        NavigableMap<LocalDate, BigDecimal> window =
                weightByDate.tailMap(referenceDate.minusDays(RATE_WINDOW_DAYS), true);
        if (window.size() < 2) {
            return null;
        }
        LocalDate first = window.firstKey();
        if (ChronoUnit.DAYS.between(first, window.lastKey()) < MIN_RATE_SPAN_DAYS) {
            return null;
        }
        int count = window.size();
        double meanX = 0;
        double meanY = 0;
        for (Map.Entry<LocalDate, BigDecimal> point : window.entrySet()) {
            meanX += ChronoUnit.DAYS.between(first, point.getKey());
            meanY += point.getValue().doubleValue();
        }
        meanX /= count;
        meanY /= count;
        double covariance = 0;
        double variance = 0;
        for (Map.Entry<LocalDate, BigDecimal> point : window.entrySet()) {
            double dx = ChronoUnit.DAYS.between(first, point.getKey()) - meanX;
            covariance += dx * (point.getValue().doubleValue() - meanY);
            variance += dx * dx;
        }
        double slopePerDay = covariance / variance;
        return BigDecimal.valueOf(slopePerDay).multiply(DAYS_PER_WEEK).setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDate projectedDate(LocalDate measuredOn, BigDecimal latestWeight, BigDecimal weeklyRate,
                                           BigDecimal goalWeight) {
        if (goalWeight == null) {
            return null;
        }
        int rateSign = weeklyRate.signum();
        int gap = goalWeight.compareTo(latestWeight);
        boolean towardGoal = (rateSign < 0 && gap < 0) || (rateSign > 0 && gap > 0);
        if (!towardGoal) {
            return null;
        }
        BigDecimal days = goalWeight.subtract(latestWeight).abs()
                .multiply(DAYS_PER_WEEK)
                .divide(weeklyRate.abs(), 0, RoundingMode.CEILING);
        if (days.compareTo(BigDecimal.valueOf(MAX_PROJECTION_DAYS)) > 0) {
            return null;
        }
        return measuredOn.plusDays(days.longValueExact());
    }
}
