package com.fitsync.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.fitsync.user.dto.ProgressInsightResponse;
import com.fitsync.user.service.ProgressInsightCalculator.GoalTarget;
import com.fitsync.user.service.ProgressInsightCalculator.WeightSample;
import org.junit.jupiter.api.Test;

class ProgressInsightCalculatorTests {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final LocalDate REFERENCE = LocalDate.parse("2026-09-30");

    @Test
    void steadyLossGivesExactRateAndProjectedDate() {
        // 74.0 on 2 Sep down to 72.0 on 30 Sep is exactly half a kilo a week.
        List<WeightSample> samples = weekly("2026-09-02", "74.0", "73.5", "73.0", "72.5", "72.0");

        ProgressInsightResponse result = calculate(samples, goal("70.0"));

        assertEquals(new BigDecimal("-0.50"), result.weeklyRateKg());
        assertEquals(28, result.rateWindowDays());
        assertEquals(new BigDecimal("72.0"), result.weightKg());
        assertEquals(LocalDate.parse("2026-09-30"), result.measuredOn());
        assertEquals(new BigDecimal("70.00"), result.goalWeightKg());
        assertEquals(LocalDate.parse("2026-12-09"), result.goalDate());
        // Two kilos at half a kilo a week is 28 days from the last weigh-in.
        assertEquals(LocalDate.parse("2026-10-28"), result.projectedDate());
    }

    @Test
    void gainTowardHigherGoalIsProjected() {
        List<WeightSample> samples = weekly("2026-09-02", "60.0", "60.7", "61.4", "62.1", "62.8");

        ProgressInsightResponse result = calculate(samples, goal("65.0"));

        assertEquals(new BigDecimal("0.70"), result.weeklyRateKg());
        // 2.2 kg at 0.7 kg a week is exactly 22 days.
        assertEquals(LocalDate.parse("2026-10-22"), result.projectedDate());
    }

    @Test
    void projectionRoundsPartialDaysUp() {
        List<WeightSample> samples = weekly("2026-09-02", "74.0", "73.5", "73.0", "72.5", "72.0");

        // 1.1 kg at 0.5 kg a week is 15.4 days, which counts as 16.
        ProgressInsightResponse result = calculate(samples, goal("70.9"));

        assertEquals(LocalDate.parse("2026-10-16"), result.projectedDate());
    }

    @Test
    void rateMovingAwayFromGoalHasNoProjection() {
        List<WeightSample> samples = weekly("2026-09-02", "74.0", "73.5", "73.0", "72.5", "72.0");

        ProgressInsightResponse result = calculate(samples, goal("80.0"));

        assertEquals(new BigDecimal("-0.50"), result.weeklyRateKg());
        assertNull(result.projectedDate());
        assertEquals(new BigDecimal("80.00"), result.goalWeightKg());
    }

    @Test
    void goalAlreadyReachedHasNoProjection() {
        List<WeightSample> samples = weekly("2026-09-02", "74.0", "73.5", "73.0", "72.5", "72.0");

        assertNull(calculate(samples, goal("72.0")).projectedDate());

        List<WeightSample> gaining = weekly("2026-09-02", "60.0", "60.7", "61.4", "62.1", "62.8");
        assertNull(calculate(gaining, goal("62.0")).projectedDate());
    }

    @Test
    void singlePointGivesNoRate() {
        ProgressInsightResponse result = calculate(List.of(sample("2026-09-29T08:00:00Z", "71.4")), goal("68.0"));

        assertEquals(new BigDecimal("71.4"), result.weightKg());
        assertEquals(1, result.entries());
        assertNull(result.weeklyRateKg());
        assertNull(result.rateWindowDays());
        assertNull(result.projectedDate());
    }

    @Test
    void twoPointsThreeDaysApartGiveNoRate() {
        List<WeightSample> samples = List.of(
                sample("2026-09-26T08:00:00Z", "72.0"),
                sample("2026-09-29T08:00:00Z", "71.0"));

        ProgressInsightResponse result = calculate(samples, goal("68.0"));

        assertEquals(2, result.entries());
        assertNull(result.weeklyRateKg());
        assertNull(result.rateWindowDays());
    }

    @Test
    void twoPointsExactlySevenDaysApartGiveARate() {
        List<WeightSample> samples = List.of(
                sample("2026-09-23T08:00:00Z", "72.0"),
                sample("2026-09-30T08:00:00Z", "71.0"));

        assertEquals(new BigDecimal("-1.00"), calculate(samples, null).weeklyRateKg());
    }

    @Test
    void severalMeasurementsOnOneDateKeepTheLatest() {
        List<WeightSample> samples = List.of(
                sample("2026-09-29T06:00:00Z", "72.6"),
                sample("2026-09-29T21:00:00Z", "71.4"),
                sample("2026-09-29T12:00:00Z", "72.0"));

        ProgressInsightResponse result = calculate(samples, null);

        assertEquals(1, result.entries());
        assertEquals(new BigDecimal("71.4"), result.series().get(0).weightKg());
        assertEquals(new BigDecimal("71.4"), result.weightKg());
    }

    @Test
    void pointsOlderThan28DaysAreLeftOutOfTheRateAndOlderThan56OutOfTheSeries() {
        List<WeightSample> samples = new ArrayList<>(
                weekly("2026-09-02", "74.0", "73.5", "73.0", "72.5", "72.0"));
        samples.add(sample("2026-08-20T08:00:00Z", "90.0"));
        samples.add(sample("2026-07-20T08:00:00Z", "99.0"));

        ProgressInsightResponse result = calculate(samples, null);

        assertEquals(new BigDecimal("-0.50"), result.weeklyRateKg());
        assertEquals(6, result.entries());
        assertEquals(LocalDate.parse("2026-08-20"), result.series().get(0).date());
        assertEquals(LocalDate.parse("2026-09-30"), result.series().get(5).date());
    }

    @Test
    void latestOlderThanTheSeriesWindowStillReportsWeight() {
        ProgressInsightResponse result = calculate(List.of(sample("2026-07-20T08:00:00Z", "99.04")), goal("90.0"));

        assertEquals(new BigDecimal("99.0"), result.weightKg());
        assertEquals(LocalDate.parse("2026-07-20"), result.measuredOn());
        assertEquals(0, result.entries());
        assertTrue(result.series().isEmpty());
        assertNull(result.weeklyRateKg());
    }

    @Test
    void projectionBeyondTwoYearsIsLeftOut() {
        // A drop of 0.04 kg in 28 days is 0.01 kg a week, so 40 kg would take decades.
        List<WeightSample> samples = List.of(
                sample("2026-09-02T08:00:00Z", "100.00"),
                sample("2026-09-30T08:00:00Z", "99.96"));

        ProgressInsightResponse result = calculate(samples, goal("60.0"));

        assertEquals(new BigDecimal("-0.01"), result.weeklyRateKg());
        assertNull(result.projectedDate());
    }

    @Test
    void zeroRateHasNoProjection() {
        List<WeightSample> samples = List.of(
                sample("2026-09-02T08:00:00Z", "72.0"),
                sample("2026-09-30T08:00:00Z", "72.0"));

        ProgressInsightResponse result = calculate(samples, goal("70.0"));

        assertEquals(0, BigDecimal.ZERO.compareTo(result.weeklyRateKg()));
        assertNull(result.projectedDate());
    }

    @Test
    void valuesAreRoundedHalfUp() {
        // One kilo over nine days is 0.7777... a week; the weights round to one decimal.
        List<WeightSample> samples = List.of(
                sample("2026-09-21T08:00:00Z", "80.00"),
                sample("2026-09-30T08:00:00Z", "79.00"));
        ProgressInsightResponse rate = calculate(samples, null);
        assertEquals(new BigDecimal("-0.78"), rate.weeklyRateKg());

        assertEquals(new BigDecimal("71.5"),
                calculate(List.of(sample("2026-09-29T08:00:00Z", "71.45")), null).weightKg());
        assertEquals(new BigDecimal("71.4"),
                calculate(List.of(sample("2026-09-29T08:00:00Z", "71.44")), null).weightKg());
    }

    @Test
    void measurementNearMidnightLandsOnTheLocalDate() {
        ZoneId singapore = ZoneId.of("Asia/Singapore");
        List<WeightSample> samples = List.of(
                // 01:30 on 30 September in Singapore, still the 29th in UTC.
                sample("2026-09-29T17:30:00Z", "71.4"),
                // 00:30 on 1 October in Singapore, which is after the reference date.
                sample("2026-09-30T16:30:00Z", "60.0"));

        ProgressInsightResponse result = ProgressInsightCalculator.calculate(
                singapore, REFERENCE, samples, null);

        assertEquals("Asia/Singapore", result.timezone());
        assertEquals(LocalDate.parse("2026-09-30"), result.measuredOn());
        assertEquals(1, result.entries());
        assertEquals(new BigDecimal("71.4"), result.weightKg());
    }

    @Test
    void noMeasurementsLeavesEverythingButTheGoalEmpty() {
        ProgressInsightResponse result = calculate(List.of(), goal("68.0"));

        assertEquals("UTC", result.timezone());
        assertEquals(REFERENCE, result.referenceDate());
        assertNull(result.weightKg());
        assertNull(result.measuredOn());
        assertEquals(0, result.entries());
        assertNull(result.weeklyRateKg());
        assertNull(result.rateWindowDays());
        assertNull(result.projectedDate());
        assertTrue(result.series().isEmpty());
        assertEquals(new BigDecimal("68.00"), result.goalWeightKg());
    }

    @Test
    void measurementsWithoutWeightAreIgnored() {
        List<WeightSample> samples = List.of(
                new WeightSample(Instant.parse("2026-09-29T08:00:00Z"), null),
                sample("2026-09-28T08:00:00Z", "71.0"));

        ProgressInsightResponse result = calculate(samples, null);

        assertEquals(1, result.entries());
        assertEquals(LocalDate.parse("2026-09-28"), result.measuredOn());
        assertNull(result.goalWeightKg());
        assertNull(result.goalDate());
    }

    private static ProgressInsightResponse calculate(List<WeightSample> samples, GoalTarget goal) {
        return ProgressInsightCalculator.calculate(UTC, REFERENCE, samples, goal);
    }

    private static GoalTarget goal(String weightKg) {
        return new GoalTarget(new BigDecimal(weightKg), LocalDate.parse("2026-12-09"));
    }

    private static WeightSample sample(String instant, String weightKg) {
        return new WeightSample(Instant.parse(instant), new BigDecimal(weightKg));
    }

    private static List<WeightSample> weekly(String firstDate, String... weights) {
        List<WeightSample> samples = new ArrayList<>();
        LocalDate date = LocalDate.parse(firstDate);
        for (String weight : weights) {
            samples.add(sample(date + "T08:00:00Z", weight));
            date = date.plusDays(7);
        }
        return samples;
    }
}
