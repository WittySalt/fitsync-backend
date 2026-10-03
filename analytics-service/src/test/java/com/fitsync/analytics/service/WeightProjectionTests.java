package com.fitsync.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

/**
 * Every case of user-service's ProgressInsightCalculatorTests, fed as the daily weights analytics stores
 * (one weight a date), must give the same latest weight, rate and projected date. The expected values are
 * copied from those tests, so a change on either side that breaks the agreement fails here.
 */
class WeightProjectionTests {

    private static final LocalDate REFERENCE = LocalDate.parse("2026-09-30");

    @Test
    void steadyLossGivesExactRateAndProjectedDate() {
        WeightProjection.Result result = calculate(weekly("2026-09-02", "74.0", "73.5", "73.0", "72.5", "72.0"), "70.0");

        assertThat(result.weeklyRateKg()).isEqualByComparingTo("-0.50");
        assertThat(result.weeklyRateKg().scale()).isEqualTo(2);
        assertThat(result.latestKg()).isEqualTo(new BigDecimal("72.0"));
        assertThat(result.measuredOn()).isEqualTo(REFERENCE);
        assertThat(result.goalKg()).isEqualTo(new BigDecimal("70.00"));
        assertThat(result.projectedDate()).isEqualTo(LocalDate.parse("2026-10-28"));
    }

    @Test
    void gainTowardHigherGoalIsProjected() {
        WeightProjection.Result result = calculate(weekly("2026-09-02", "60.0", "60.7", "61.4", "62.1", "62.8"), "65.0");

        assertThat(result.weeklyRateKg()).isEqualTo(new BigDecimal("0.70"));
        assertThat(result.projectedDate()).isEqualTo(LocalDate.parse("2026-10-22"));
    }

    @Test
    void projectionRoundsPartialDaysUp() {
        assertThat(calculate(weekly("2026-09-02", "74.0", "73.5", "73.0", "72.5", "72.0"), "70.9").projectedDate())
                .isEqualTo(LocalDate.parse("2026-10-16"));
    }

    @Test
    void rateMovingAwayFromGoalOrGoalReachedHasNoProjection() {
        Map<LocalDate, BigDecimal> losing = weekly("2026-09-02", "74.0", "73.5", "73.0", "72.5", "72.0");
        assertThat(calculate(losing, "80.0").projectedDate()).isNull();
        assertThat(calculate(losing, "80.0").goalKg()).isEqualTo(new BigDecimal("80.00"));
        assertThat(calculate(losing, "72.0").projectedDate()).isNull();
        assertThat(calculate(weekly("2026-09-02", "60.0", "60.7", "61.4", "62.1", "62.8"), "62.0").projectedDate())
                .isNull();
    }

    @Test
    void tooFewOrTooCloseWeighInsGiveNoRate() {
        assertThat(calculate(Map.of(LocalDate.parse("2026-09-29"), new BigDecimal("71.4")), "68.0").weeklyRateKg())
                .isNull();
        Map<LocalDate, BigDecimal> close = Map.of(LocalDate.parse("2026-09-26"), new BigDecimal("72.0"),
                LocalDate.parse("2026-09-29"), new BigDecimal("71.0"));
        assertThat(calculate(close, "68.0").weeklyRateKg()).isNull();
        Map<LocalDate, BigDecimal> sevenDays = Map.of(LocalDate.parse("2026-09-23"), new BigDecimal("72.0"),
                REFERENCE, new BigDecimal("71.0"));
        assertThat(calculate(sevenDays, null).weeklyRateKg()).isEqualTo(new BigDecimal("-1.00"));
    }

    @Test
    void pointsOlderThan28DaysAreLeftOutOfTheRate() {
        Map<LocalDate, BigDecimal> weights = new TreeMap<>(weekly("2026-09-02", "74.0", "73.5", "73.0", "72.5", "72.0"));
        weights.put(LocalDate.parse("2026-08-20"), new BigDecimal("90.0"));
        weights.put(LocalDate.parse("2026-07-20"), new BigDecimal("99.0"));

        assertThat(calculate(weights, null).weeklyRateKg()).isEqualTo(new BigDecimal("-0.50"));
    }

    @Test
    void latestOlderThanTheWindowStillReportsWeightAndWeighInsAfterTheReferenceAreIgnored() {
        WeightProjection.Result result = calculate(Map.of(LocalDate.parse("2026-07-20"), new BigDecimal("99.04"),
                LocalDate.parse("2026-10-01"), new BigDecimal("60.0")), "90.0");

        assertThat(result.latestKg()).isEqualTo(new BigDecimal("99.0"));
        assertThat(result.measuredOn()).isEqualTo(LocalDate.parse("2026-07-20"));
        assertThat(result.weeklyRateKg()).isNull();
    }

    @Test
    void projectionBeyondTwoYearsAndZeroRateAreLeftOut() {
        Map<LocalDate, BigDecimal> slow = Map.of(LocalDate.parse("2026-09-02"), new BigDecimal("100.00"),
                REFERENCE, new BigDecimal("99.96"));
        assertThat(calculate(slow, "60.0").weeklyRateKg()).isEqualTo(new BigDecimal("-0.01"));
        assertThat(calculate(slow, "60.0").projectedDate()).isNull();

        Map<LocalDate, BigDecimal> flat = Map.of(LocalDate.parse("2026-09-02"), new BigDecimal("72.0"),
                REFERENCE, new BigDecimal("72.0"));
        assertThat(calculate(flat, "70.0").weeklyRateKg()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(calculate(flat, "70.0").projectedDate()).isNull();
    }

    @Test
    void valuesAreRoundedHalfUp() {
        Map<LocalDate, BigDecimal> nineDays = Map.of(LocalDate.parse("2026-09-21"), new BigDecimal("80.00"),
                REFERENCE, new BigDecimal("79.00"));
        assertThat(calculate(nineDays, null).weeklyRateKg()).isEqualTo(new BigDecimal("-0.78"));
        assertThat(calculate(Map.of(LocalDate.parse("2026-09-29"), new BigDecimal("71.45")), null).latestKg())
                .isEqualTo(new BigDecimal("71.5"));
        assertThat(calculate(Map.of(LocalDate.parse("2026-09-29"), new BigDecimal("71.44")), null).latestKg())
                .isEqualTo(new BigDecimal("71.4"));
    }

    @Test
    void noWeighInsLeaveEverythingButTheGoalEmpty() {
        WeightProjection.Result result = calculate(Map.of(), "68.0");

        assertThat(result.latestKg()).isNull();
        assertThat(result.measuredOn()).isNull();
        assertThat(result.weeklyRateKg()).isNull();
        assertThat(result.projectedDate()).isNull();
        assertThat(result.goalKg()).isEqualTo(new BigDecimal("68.00"));
    }

    private static WeightProjection.Result calculate(Map<LocalDate, BigDecimal> weights, String goalKg) {
        return WeightProjection.calculate(weights, REFERENCE, goalKg == null ? null : new BigDecimal(goalKg));
    }

    private static Map<LocalDate, BigDecimal> weekly(String firstDate, String... weights) {
        Map<LocalDate, BigDecimal> map = new TreeMap<>();
        LocalDate date = LocalDate.parse(firstDate);
        for (String weight : weights) {
            map.put(date, new BigDecimal(weight));
            date = date.plusDays(7);
        }
        return map;
    }
}
