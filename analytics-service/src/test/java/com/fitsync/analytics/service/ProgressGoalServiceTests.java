package com.fitsync.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.fitsync.analytics.dto.ProgressGoalResponse;
import com.fitsync.analytics.dto.ProgressGoalResponse.DayState;
import com.fitsync.analytics.dto.ProgressGoalResponse.OtherGoal;
import com.fitsync.analytics.dto.ProgressGoalResponse.WeeklyWeight;
import com.fitsync.analytics.dto.ProgressHighlight;
import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;
import com.fitsync.analytics.entity.AnalyticsUserGoalProgressEntity;
import org.junit.jupiter.api.Test;

class ProgressGoalServiceTests {

    private static final LocalDate FROM = LocalDate.parse("2026-09-07");
    private static final LocalDate TO = LocalDate.parse("2026-10-04");
    private static final LocalDate GOAL_DATE = LocalDate.parse("2026-12-11");
    private static final String SATURDAY_NOON = "2026-10-03T12:00:00Z";

    private final ProgressFixture fixture = new ProgressFixture();

    /** 75 kg on 13 July, 0.04 kg a day down until 13 Sept, then 0.06 kg a day, weighed every day to 1 Oct. */
    private Map<LocalDate, BigDecimal> weighDaily() {
        Map<LocalDate, BigDecimal> weights = new TreeMap<>();
        BigDecimal kg = new BigDecimal("75.00");
        for (LocalDate date = LocalDate.parse("2026-07-13"); !date.isAfter(LocalDate.parse("2026-10-01"));
             date = date.plusDays(1)) {
            fixture.body(date, kg.toPlainString());
            weights.put(date, kg);
            kg = kg.subtract(date.isBefore(LocalDate.parse("2026-09-13")) ? new BigDecimal("0.04") : new BigDecimal("0.06"));
        }
        return weights;
    }

    @Test
    void weightFiguresUseTheSameProjectionAsUserServiceAndCompareWithThePeriodBefore() {
        Map<LocalDate, BigDecimal> weights = weighDaily();
        fixture.goal("WEIGHT", "68.0", GOAL_DATE, "2026-10-01T00:00:00Z");
        fixture.body(LocalDate.parse("2026-09-12"), null).setWaistCm(new BigDecimal("85.9"));
        fixture.body(LocalDate.parse("2026-10-03"), null).setWaistCm(new BigDecimal("83.8"));
        fixture.wire();

        ProgressGoalResponse response = fixture.goalService(ProgressFixture.clockAt(SATURDAY_NOON))
                .getGoal(fixture.userId, ProgressWindow.of(FROM, TO), "UTC");

        WeightProjection.Result now = WeightProjection.calculate(weights, LocalDate.parse("2026-10-03"),
                new BigDecimal("68.0"));
        assertThat(response.previous().from()).isEqualTo(LocalDate.parse("2026-08-10"));
        assertThat(response.previous().to()).isEqualTo(LocalDate.parse("2026-09-06"));
        assertThat(response.goal().targetKg()).isEqualByComparingTo("68.0");
        assertThat(response.hero().weeklyRate().value()).isEqualTo(now.weeklyRateKg());
        assertThat(response.hero().weeklyRate().previous()).isEqualTo(new BigDecimal("-0.28"));
        assertThat(response.hero().projectedDate().value()).isEqualTo(now.projectedDate()).isNotNull();
        assertThat(response.hero().projectedDate().previous()).isNotNull();
        assertThat(response.hero().nowKg()).isEqualTo(now.latestKg());
        assertThat(response.hero().measuredOn()).isEqualTo(LocalDate.parse("2026-10-01"));
        assertThat(response.hero().daysLeft()).isEqualTo(69);

        assertThat(response.weight().method()).isEqualTo("28-day average change carried forward");
        assertThat(response.weight().daily()).hasSize(25);
        assertThat(response.weight().weekly()).extracting(WeeklyWeight::weighIns).containsExactly(7, 7, 7, 4);
        assertThat(response.weight().weekly()).extracting(WeeklyWeight::weekStart)
                .allMatch(date -> date.getDayOfWeek() == DayOfWeek.MONDAY);
        BigDecimal lastWeek = response.weight().weekly().get(3).avgKg();
        BigDecimal weekOf31Aug = ProgressGoalService.weeklyWeights(weights, LocalDate.parse("2026-08-10"),
                LocalDate.parse("2026-09-06")).get(3).avgKg();
        assertThat(response.hero().changeKg()).isEqualTo(lastWeek.subtract(weekOf31Aug).setScale(2, RoundingMode.HALF_UP));
        assertThat(response.weight().projection()).hasSize(2);
        assertThat(response.weight().projection().get(1).kg()).isEqualTo(new BigDecimal("68.00"));
        assertThat(response.weight().rateChangeMark()).isNotNull();
        // The pace changed between 13 and 14 Sept: 0.28 kg a week before, 0.42 after.
        assertThat(response.weight().rateChangeMark().text()).isEqualTo("Faster from 14 Sept");
        assertThat(response.weight().rateChangeMark().date()).isEqualTo(LocalDate.parse("2026-09-14"));
        assertThat(response.weight().rateChangeMark().rateBefore()).isEqualTo(new BigDecimal("-0.28"));
        assertThat(response.weight().rateChangeMark().rateAfter()).isEqualTo(new BigDecimal("-0.42"));

        assertThat(response.body().waist().start()).isEqualByComparingTo("85.9");
        assertThat(response.body().waist().end()).isEqualByComparingTo("83.8");
        assertThat(response.body().waist().context()).isEqualTo("−2.1 cm since 12 Sept");
        assertThat(response.body().bodyFat().series()).isEmpty();
        assertThat(response.highlights()).extracting(ProgressHighlight::code)
                .containsExactly("RATE_IN_RANGE", "WAIST_VS_WEIGHT");
        assertThat(response.pace().weeks()).hasSize(8);
    }

    @Test
    void noWeightGoalAndNoDataGiveEmptySectionsAndNoHighlights() {
        fixture.wire();

        ProgressGoalResponse response = fixture.goalService(ProgressFixture.clockAt(SATURDAY_NOON))
                .getGoal(fixture.userId, ProgressWindow.of(FROM, TO), null);

        assertThat(response.goal()).isNull();
        assertThat(response.hero().nowKg()).isNull();
        assertThat(response.hero().weeklyRate().value()).isNull();
        assertThat(response.hero().daysLeft()).isNull();
        assertThat(response.weight().daily()).isEmpty();
        assertThat(response.weight().projection()).isEmpty();
        assertThat(response.weight().rateChangeMark()).isNull();
        assertThat(response.highlights()).isEmpty();
        assertThat(response.otherGoals()).isEmpty();
    }

    @Test
    void theNewestWeighInBeforeEveryWindowStillAnswersNow() {
        fixture.body(LocalDate.parse("2026-01-05"), "80.04");
        fixture.wire();

        ProgressGoalResponse response = fixture.goalService(ProgressFixture.clockAt(SATURDAY_NOON))
                .getGoal(fixture.userId, ProgressWindow.of(FROM, TO), "UTC");

        assertThat(response.hero().nowKg()).isEqualTo(new BigDecimal("80.0"));
        assertThat(response.weight().daily()).isEmpty();
    }

    private void proteinWeek() {
        fixture.goal("PROTEIN", "140", null, "2026-10-01T00:00:00Z");
        // An older read-model row of the same type is ignored.
        fixture.goal("PROTEIN", "100", null, "2026-06-01T00:00:00Z");
        fixture.day(LocalDate.parse("2026-09-07"), 2000, 3, "150");
        fixture.day(LocalDate.parse("2026-09-08"), 2000, 3, "120");
        fixture.day(LocalDate.parse("2026-09-09"), 2000, 2, "141");
        fixture.day(LocalDate.parse("2026-09-10"), 0, 0, "0");
        fixture.day(LocalDate.parse("2026-10-04"), 1800, 2, "150");
        fixture.wire();
    }

    @Test
    void otherGoalsMarkEachDayFromTheDailyRowsAndFeedTheProteinHighlight() {
        proteinWeek();

        ProgressGoalResponse response = fixture.goalService(ProgressFixture.clockAt(SATURDAY_NOON))
                .getGoal(fixture.userId, ProgressWindow.of(FROM, TO), "UTC");

        OtherGoal protein = response.otherGoals().get(0);
        assertThat(protein.type()).isEqualTo("PROTEIN");
        assertThat(protein.target()).isEqualByComparingTo("140");
        assertThat(protein.unit()).isEqualTo("g");
        assertThat(protein.metDays()).isEqualTo(2);
        assertThat(protein.loggedDays()).isEqualTo(3);
        assertThat(protein.days()).hasSize(28);
        assertThat(state(protein, "2026-09-07")).isEqualTo("MET");
        assertThat(state(protein, "2026-09-08")).isEqualTo("MISSED");
        assertThat(state(protein, "2026-09-10")).isEqualTo("NOT_LOGGED");
        assertThat(state(protein, "2026-10-04")).isEqualTo("FUTURE");
        assertThat(protein.weeks()).isEmpty();
        ProgressHighlight proteinMet = response.highlights().stream()
                .filter(highlight -> highlight.code().equals("PROTEIN_MET")).findFirst().orElseThrow();
        assertThat(proteinMet.title()).isEqualTo("Protein was met on 2 of 3 logged days.");
    }

    @Test
    void theCallersZoneDecidesWhichDayIsToday() {
        proteinWeek();
        // 20:00 UTC on Saturday is already Sunday 4 Oct in Singapore.
        String saturdayEvening = "2026-10-03T20:00:00Z";

        ProgressGoalResponse utc = fixture.goalService(ProgressFixture.clockAt(saturdayEvening))
                .getGoal(fixture.userId, ProgressWindow.of(FROM, TO), "UTC");
        ProgressGoalResponse singapore = fixture.goalService(ProgressFixture.clockAt(saturdayEvening))
                .getGoal(fixture.userId, ProgressWindow.of(FROM, TO), "Asia/Singapore");

        assertThat(state(utc.otherGoals().get(0), "2026-10-04")).isEqualTo("FUTURE");
        assertThat(state(singapore.otherGoals().get(0), "2026-10-04")).isEqualTo("MET");
        assertThat(singapore.otherGoals().get(0).loggedDays()).isEqualTo(4);

        // Without a header the stored zone is used.
        fixture.storedZone("Asia/Singapore");
        ProgressGoalResponse stored = fixture.goalService(ProgressFixture.clockAt(saturdayEvening))
                .getGoal(fixture.userId, ProgressWindow.of(FROM, TO), null);
        assertThat(state(stored.otherGoals().get(0), "2026-10-04")).isEqualTo("MET");
    }

    @Test
    void workoutFrequencyCountsMondayWeeksAndLeavesAnUnfinishedUnmetWeekOut() {
        AnalyticsUserGoalProgressEntity goal = fixture.goal("WORKOUT_FREQUENCY", "3", null, "2026-10-01T00:00:00Z");
        Map<LocalDate, Integer> sessions = new HashMap<>();
        // Sunday 6 Sept belongs to the week before the period and must not count.
        sessions.put(LocalDate.parse("2026-09-06"), 1);
        sessions.put(LocalDate.parse("2026-09-07"), 1);
        sessions.put(LocalDate.parse("2026-09-09"), 1);
        sessions.put(LocalDate.parse("2026-09-13"), 1);
        sessions.put(LocalDate.parse("2026-09-15"), 2);
        sessions.put(LocalDate.parse("2026-09-21"), 4);
        sessions.put(LocalDate.parse("2026-09-29"), 1);

        OtherGoal frequency = ProgressGoalService.workoutFrequency(goal, ProgressWindow.of(FROM, TO), sessions,
                LocalDate.parse("2026-10-03"));

        assertThat(frequency.weeks()).extracting(week -> week.value().intValue()).containsExactly(3, 2, 4, 1);
        assertThat(frequency.metDays()).isEqualTo(2);
        assertThat(frequency.loggedDays()).isEqualTo(3);
        assertThat(state(frequency, "2026-09-07")).isEqualTo("MET");
        assertThat(state(frequency, "2026-09-08")).isEqualTo("NOT_LOGGED");
        assertThat(state(frequency, "2026-10-04")).isEqualTo("FUTURE");
        assertThat(frequency.unit()).isEqualTo("sessions");
    }

    @Test
    void caloriesCarbsAndFatAreMetWithinTenPercentAndTheRowTargetWins() {
        AnalyticsUserDailyNutritionEntity row = fixture.day(FROM, 2300, 3, "100");
        assertThat(ProgressGoalService.reached("CALORIES", row, new BigDecimal("2100"))).isTrue();
        row.setTotalCaloriesConsumed(2311);
        assertThat(ProgressGoalService.reached("CALORIES", row, new BigDecimal("2100"))).isFalse();
        row.setCalorieTarget(2300);
        assertThat(ProgressGoalService.reached("CALORIES", row, new BigDecimal("2100"))).isTrue();
        row.setTotalFatGrams(new BigDecimal("54"));
        assertThat(ProgressGoalService.reached("FAT", row, new BigDecimal("60"))).isTrue();
        row.setTotalCarbsGrams(new BigDecimal("200"));
        assertThat(ProgressGoalService.reached("CARBS", row, new BigDecimal("236"))).isFalse();
        row.setProteinTargetGrams(new BigDecimal("90"));
        assertThat(ProgressGoalService.reached("PROTEIN", row, new BigDecimal("140"))).isTrue();
    }

    @Test
    void weeklyAveragesSplitOnMonday() {
        Map<LocalDate, BigDecimal> weights = Map.of(
                LocalDate.parse("2026-09-06"), new BigDecimal("72.0"),
                LocalDate.parse("2026-09-07"), new BigDecimal("71.0"),
                LocalDate.parse("2026-09-08"), new BigDecimal("71.4"));

        assertThat(ProgressGoalService.weeklyWeights(weights, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30")))
                .containsExactly(new WeeklyWeight(LocalDate.parse("2026-08-31"), new BigDecimal("72.00"), 1),
                        new WeeklyWeight(LocalDate.parse("2026-09-07"), new BigDecimal("71.20"), 2));
    }

    private static String state(OtherGoal goal, String date) {
        return goal.days().stream().filter(day -> day.date().equals(LocalDate.parse(date)))
                .map(DayState::state).findFirst().orElseThrow();
    }
}
