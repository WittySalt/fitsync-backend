package com.fitsync.user.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import com.fitsync.user.dto.ProgressInsightPoint;
import com.fitsync.user.dto.ProgressInsightResponse;

/**
 * Turns raw weigh-ins and the weight goal into the progress reading. It holds no state and reads no
 * clock, so the arithmetic can be checked against hand-worked numbers.
 */
final class ProgressInsightCalculator {

    /** The chart shows the last eight weeks of daily weights. */
    static final int SERIES_DAYS = 56;
    static final int RATE_WINDOW_DAYS = 28;
    /** Weigh-ins closer together than a week say too little about a trend to give a rate. */
    static final int MIN_RATE_SPAN_DAYS = 7;
    /** A projection more than two years out is not shown. */
    static final int MAX_PROJECTION_DAYS = 730;

    private static final BigDecimal DAYS_PER_WEEK = BigDecimal.valueOf(7);

    /** One weigh-in as stored: the moment it was measured and the weight it carried. */
    record WeightSample(Instant measuredAt, BigDecimal weightKg) {
    }

    /** The active weight goal of the user. */
    record GoalTarget(BigDecimal weightKg, LocalDate date) {
    }

    private ProgressInsightCalculator() {
    }

    static ProgressInsightResponse calculate(ZoneId zone,
                                             LocalDate referenceDate,
                                             List<WeightSample> samples,
                                             GoalTarget goal) {
        NavigableMap<LocalDate, BigDecimal> weightByDate = dailyWeights(zone, referenceDate, samples);

        BigDecimal goalWeight = goal == null || goal.weightKg() == null
                ? null
                : goal.weightKg().setScale(2, RoundingMode.HALF_UP);
        LocalDate goalDate = goalWeight == null ? null : goal.date();

        if (weightByDate.isEmpty()) {
            return new ProgressInsightResponse(zone.getId(), referenceDate, null, null, 0, null, null,
                    goalWeight, goalDate, null, List.of());
        }

        List<ProgressInsightPoint> series = new ArrayList<>();
        weightByDate.tailMap(referenceDate.minusDays(SERIES_DAYS), true).forEach((date, weight) ->
                series.add(new ProgressInsightPoint(date, oneDecimal(weight))));

        Map.Entry<LocalDate, BigDecimal> latest = weightByDate.lastEntry();
        BigDecimal latestWeight = oneDecimal(latest.getValue());

        BigDecimal weeklyRate = weeklyRate(weightByDate, referenceDate);
        LocalDate projected = weeklyRate == null
                ? null
                : projectedDate(latest.getKey(), latestWeight, weeklyRate, goalWeight);

        return new ProgressInsightResponse(
                zone.getId(),
                referenceDate,
                latestWeight,
                latest.getKey(),
                series.size(),
                weeklyRate,
                weeklyRate == null ? null : RATE_WINDOW_DAYS,
                goalWeight,
                goalDate,
                projected,
                series);
    }

    /** Keeps the latest weigh-in of each local date and drops anything after the reference date. */
    private static NavigableMap<LocalDate, BigDecimal> dailyWeights(ZoneId zone,
                                                                    LocalDate referenceDate,
                                                                    List<WeightSample> samples) {
        TreeMap<LocalDate, WeightSample> latestOfDate = new TreeMap<>();
        for (WeightSample sample : samples) {
            if (sample.weightKg() == null || sample.measuredAt() == null) {
                continue;
            }
            LocalDate date = sample.measuredAt().atZone(zone).toLocalDate();
            if (date.isAfter(referenceDate)) {
                continue;
            }
            latestOfDate.merge(date, sample,
                    (kept, other) -> other.measuredAt().isAfter(kept.measuredAt()) ? other : kept);
        }
        TreeMap<LocalDate, BigDecimal> weights = new TreeMap<>();
        latestOfDate.forEach((date, sample) -> weights.put(date, sample.weightKg()));
        return weights;
    }

    /** Slope of a least-squares line through the last 28 days, per week; null when it cannot be trusted. */
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

    /**
     * Counts days from the last weigh-in at the rounded pace. A goal that is already reached, or a pace
     * that leads away from it, has no projection.
     */
    private static LocalDate projectedDate(LocalDate measuredOn,
                                           BigDecimal latestWeight,
                                           BigDecimal weeklyRate,
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

    private static BigDecimal oneDecimal(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP);
    }
}
