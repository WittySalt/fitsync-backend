package com.fitsync.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fitsync.analytics.dto.ProgressGoalResponse.KeepWeek;
import com.fitsync.analytics.dto.ProgressGoalResponse.Road;
import com.fitsync.analytics.dto.ProgressGoalResponse.Tick;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/** One test per road state, with the figures of board 21e of the Progress v5 design. */
class ProgressRoadTests {

    private static LocalDate day(String date) {
        return LocalDate.parse(date);
    }

    /** One weigh-in a day, straight lines between the given (date, kg) points. */
    private static Map<LocalDate, BigDecimal> weighIns(Object... points) {
        TreeMap<LocalDate, BigDecimal> weights = new TreeMap<>();
        for (int i = 0; i + 3 < points.length; i += 2) {
            LocalDate from = day((String) points[i]);
            LocalDate to = day((String) points[i + 2]);
            double kgFrom = (Double) points[i + 1];
            double kgTo = (Double) points[i + 3];
            long span = to.toEpochDay() - from.toEpochDay();
            for (long d = 0; d <= span; d++) {
                weights.put(from.plusDays(d),
                        BigDecimal.valueOf(kgFrom + (kgTo - kgFrom) * d / span).setScale(4, RoundingMode.HALF_UP));
            }
        }
        return weights;
    }

    /** 74.6 kg on 6 July, 71.2 kg on 1 October, falling 0.41 kg a week over the last 28 days. */
    private static Map<LocalDate, BigDecimal> lossAhead() {
        return weighIns("2026-07-06", 74.6, "2026-09-03", 72.84, "2026-10-01", 71.2);
    }

    private static Road loss(Map<LocalDate, BigDecimal> weights, String targetDate, String reference) {
        return ProgressRoad.build(weights, null, new BigDecimal("68.0"), day(targetDate), null, day(reference));
    }

    @Test
    void aheadWhenTheProjectedDateIsAWeekOrMoreBeforeTheGoalDate() {
        Road road = loss(lossAhead(), "2026-12-11", "2026-10-01");

        assertThat(road.direction()).isEqualTo("LOSE");
        assertThat(road.state()).isEqualTo("AHEAD");
        assertThat(road.startKg()).isEqualByComparingTo("74.6");
        assertThat(road.startDate()).isEqualTo(day("2026-07-06"));
        assertThat(road.nowKg()).isEqualByComparingTo("71.2");
        assertThat(road.nowDate()).isEqualTo(day("2026-10-01"));
        assertThat(road.distanceKg()).isEqualByComparingTo("3.2");
        assertThat(road.projectedDate()).isEqualTo(day("2026-11-25"));
        assertThat(road.fractionWeight()).isEqualByComparingTo("0.52");
        assertThat(road.fractionTime()).isEqualByComparingTo("0.55");
        assertThat(road.keep()).isNull();
        assertThat(road.ticks()).extracting(Tick::kg).containsExactly(74, 73, 72, 71, 70, 69, 68);
        // 74 to 72 are behind the weight; 71 and below are still ahead of it.
        assertThat(road.ticks().get(0).crossedOn()).isNotNull().isBefore(day("2026-10-01"));
        assertThat(road.ticks().get(2).crossedOn()).isNotNull();
        assertThat(road.ticks().get(3).crossedOn()).isNull();
        assertThat(road.ticks().get(3).projectedOn()).isAfter(day("2026-10-01"));
        assertThat(road.ticks().get(6).projectedOn()).isEqualTo(road.projectedDate());
    }

    @Test
    void onCourseWithinAWeekOfTheGoalDate() {
        assertThat(loss(lossAhead(), "2026-11-28", "2026-10-01").state()).isEqualTo("ON_COURSE");
    }

    @Test
    void behindWhenTheProjectedDateIsAWeekOrMoreAfterTheGoalDate() {
        Road road = loss(lossAhead(), "2026-11-01", "2026-10-01");

        assertThat(road.state()).isEqualTo("BEHIND");
        assertThat(road.projectedDate()).isEqualTo(day("2026-11-25"));
    }

    @Test
    void behindWhenTheWeightMovesAwayFromTheGoal() {
        Map<LocalDate, BigDecimal> weights = weighIns("2026-07-06", 74.6, "2026-09-03", 70.0, "2026-10-01", 71.2);

        Road road = loss(weights, "2026-12-11", "2026-10-01");

        assertThat(road.state()).isEqualTo("BEHIND");
        assertThat(road.projectedDate()).isNull();
        assertThat(road.ticks().get(road.ticks().size() - 1).projectedOn()).isNull();
    }

    @Test
    void gainRoadRunsFromTheLowerStartToTheHigherTarget() {
        Map<LocalDate, BigDecimal> weights = weighIns("2026-07-20", 70.4, "2026-09-03", 71.9, "2026-10-01", 73.1);

        Road road = ProgressRoad.build(weights, null, new BigDecimal("76.0"), day("2027-01-20"), null,
                day("2026-10-01"));

        assertThat(road.direction()).isEqualTo("GAIN");
        assertThat(road.startKg()).isEqualByComparingTo("70.4");
        assertThat(road.nowKg()).isEqualByComparingTo("73.1");
        assertThat(road.distanceKg()).isEqualByComparingTo("2.9");
        assertThat(road.fractionWeight()).isEqualByComparingTo("0.48");
        assertThat(road.ticks()).extracting(Tick::kg).containsExactly(71, 72, 73, 74, 75, 76);
        assertThat(road.ticks().get(1).crossedOn()).isNotNull();
        assertThat(road.ticks().get(2).crossedOn()).isNull();
        // The latest weigh-in is already past 73 kg although the week's mean is not, so it is expected today.
        assertThat(road.ticks().get(2).projectedOn()).isEqualTo(day("2026-10-01"));
        assertThat(road.ticks().get(3).projectedOn()).isAfter(day("2026-10-01"));
        assertThat(road.state()).isIn("AHEAD", "ON_COURSE", "BEHIND");
    }

    @Test
    void keepCountsTheWeeksInsideTheBand() {
        // Eight weeks around 70 kg; the sixth averages 71.4, outside the 1 kg band.
        TreeMap<LocalDate, BigDecimal> weights = new TreeMap<>();
        double[] weekly = {70.4, 70.2, 70.0, 69.8, 69.9, 71.4, 70.1, 69.9};
        LocalDate firstMonday = day("2026-08-17");
        for (int week = 0; week < weekly.length; week++) {
            for (int d = 0; d < 7; d++) {
                weights.put(firstMonday.plusWeeks(week).plusDays(d), BigDecimal.valueOf(weekly[week]));
            }
        }

        Road road = ProgressRoad.build(weights, null, new BigDecimal("70.0"), null, null, day("2026-10-11"));

        assertThat(road.direction()).isEqualTo("KEEP");
        assertThat(road.keep().bandKg()).isEqualByComparingTo("1.0");
        assertThat(road.keep().weeks()).hasSize(8);
        assertThat(road.keep().weeks().stream().filter(KeepWeek::inside).count()).isEqualTo(7);
        assertThat(road.keep().weeks().get(7).avgKg()).isEqualByComparingTo("69.90");
        assertThat(road.keep().weeks().get(7).weekStart()).isEqualTo(day("2026-10-05"));
        assertThat(road.fractionWeight()).isEqualByComparingTo("0.88");
        assertThat(road.ticks()).isEmpty();
        assertThat(road.projectedDate()).isNull();
        assertThat(road.state()).isEqualTo("ON_COURSE");
    }

    @Test
    void keepHoldsSteadyWhenInsideTheBandWithAFlatPace() {
        TreeMap<LocalDate, BigDecimal> weights = new TreeMap<>();
        for (int d = 0; d < 60; d++) {
            weights.put(day("2026-08-10").plusDays(d), new BigDecimal("70.2"));
        }

        Road road = ProgressRoad.build(weights, null, new BigDecimal("70.0"), null, null, day("2026-10-08"));

        assertThat(road.state()).isEqualTo("STALLED");
    }

    @Test
    void reachedOnTheFirstDayTheWeekMeanPassesTheTarget() {
        Map<LocalDate, BigDecimal> weights = weighIns("2026-07-06", 74.6, "2026-11-10", 69.0, "2026-11-18", 67.5,
                "2026-11-30", 67.3);

        Road road = loss(weights, "2026-12-11", "2026-11-30");

        assertThat(road.state()).isEqualTo("REACHED");
        assertThat(road.fractionWeight()).isEqualByComparingTo("1.00");
        Tick target = road.ticks().get(road.ticks().size() - 1);
        assertThat(target.kg()).isEqualTo(68);
        assertThat(target.crossedOn()).isBetween(day("2026-11-14"), day("2026-11-20"));
        assertThat(target.projectedOn()).isNull();
    }

    @Test
    void datePassedWhenTheGoalDateIsBehindAndTheTargetIsNotReached() {
        Map<LocalDate, BigDecimal> weights = weighIns("2026-07-06", 74.6, "2026-11-14", 71.0, "2026-12-12", 69.4);

        Road road = loss(weights, "2026-12-11", "2026-12-12");

        assertThat(road.state()).isEqualTo("DATE_PASSED");
        assertThat(road.nowKg()).isEqualByComparingTo("69.4");
        assertThat(road.distanceKg()).isEqualByComparingTo("1.4");
        assertThat(road.projectedDate()).isAfter(day("2026-12-12"));
        assertThat(road.fractionTime()).isEqualByComparingTo("1.00");
    }

    @Test
    void stalledWhenTheWeeklyRateIsUnderFiftyGrams() {
        Map<LocalDate, BigDecimal> weights = weighIns("2026-07-06", 74.6, "2026-09-03", 72.0, "2026-10-01", 72.0);

        Road road = loss(weights, "2026-12-11", "2026-10-01");

        assertThat(road.state()).isEqualTo("STALLED");
        assertThat(WeightProjection.calculate(weights, day("2026-10-01"), new BigDecimal("68.0")).weeklyRateKg())
                .isEqualByComparingTo("0.00");
        assertThat(road.ticks().stream().map(Tick::projectedOn).filter(Objects::nonNull)).isEmpty();
    }

    @Test
    void noProjectionUnderTwoWeighInsKeepsTheTicksWithoutDates() {
        Map<LocalDate, BigDecimal> weights = Map.of(day("2026-10-01"), new BigDecimal("74.6"));

        Road road = loss(weights, "2026-12-11", "2026-10-01");

        assertThat(road.state()).isEqualTo("NO_PROJECTION");
        assertThat(road.ticks()).extracting(Tick::kg).containsExactly(74, 73, 72, 71, 70, 69, 68);
        assertThat(road.ticks()).allMatch(tick -> tick.crossedOn() == null && tick.projectedOn() == null);
        assertThat(road.projectedDate()).isNull();
    }

    @Test
    void withoutAnyWeighInTheRoadHasOnlyTheGoal() {
        Road road = loss(Map.of(), "2026-12-11", "2026-10-01");

        assertThat(road.state()).isEqualTo("NO_PROJECTION");
        assertThat(road.direction()).isNull();
        assertThat(road.targetKg()).isEqualByComparingTo("68.0");
        assertThat(road.nowKg()).isNull();
        assertThat(road.ticks()).isEmpty();
    }

    @Test
    void theRoadStartsAtTheFirstWeighInOnOrAfterTheGoalWasSet() {
        Road road = ProgressRoad.build(lossAhead(), day("2026-08-01"), new BigDecimal("68.0"), day("2026-12-11"),
                null, day("2026-10-01"));

        assertThat(road.startDate()).isEqualTo(day("2026-08-01"));
    }

    @Test
    void theLossAheadRoadSerialisesToTheGoldenJson() throws IOException {
        Road road = loss(lossAhead(), "2026-12-11", "2026-10-01");
        ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

        JsonNode actual = mapper.readTree(mapper.writeValueAsString(road));

        try (InputStream golden = getClass().getResourceAsStream("/golden/progress-road-loss-ahead.json")) {
            assertThat(golden).isNotNull();
            assertThat(actual).isEqualTo(mapper.readTree(golden));
        }
    }
}
