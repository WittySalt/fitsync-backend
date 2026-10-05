package com.fitsync.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fitsync.analytics.dto.AnalyticsEnergyWeeklyResponse;
import com.fitsync.analytics.dto.ProgressFoodResponse;
import com.fitsync.analytics.dto.ProgressFoodResponse.MealShare;
import com.fitsync.analytics.dto.ProgressFoodResponse.PlateGroup;
import com.fitsync.analytics.dto.ProgressHighlight;
import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;
import org.junit.jupiter.api.Test;

class ProgressFoodServiceTests {

    /** Monday 21 Sept to Sunday 4 Oct; the period before is 7 to 20 Sept. */
    private static final LocalDate FROM = LocalDate.parse("2026-09-21");
    private static final LocalDate TO = LocalDate.parse("2026-10-04");
    private static final String SATURDAY_NOON = "2026-10-03T12:00:00Z";
    private static final LocalDate MONDAY = LocalDate.parse("2026-10-05");
    private static final String MONDAY_MORNING = "2026-10-05T09:00:00Z";

    private final ProgressFixture fixture = new ProgressFixture();

    private void twoPeriods() {
        fixture.profile("2026-08-01T00:00:00Z", 1750);
        // The period before: protein 150 a day, an even meal split, vegetables a small share.
        for (LocalDate date = LocalDate.parse("2026-09-14"); date.isBefore(FROM); date = date.plusDays(1)) {
            fixture.day(date, 2000, 4, "150").setFoodGroupCalories("GRAINS=900;PROTEIN=600;VEG=100;SWEETS=400");
            fixture.meal(date, "BREAKFAST", 500);
            fixture.meal(date, "LUNCH", 500);
            fixture.meal(date, "DINNER", 500);
            fixture.meal(date, "SNACK", 500);
        }
        // This period: two weeks logged, protein down to 130 in the second, dinner the largest meal.
        for (LocalDate date = FROM; date.isBefore(LocalDate.parse("2026-10-03")); date = date.plusDays(1)) {
            boolean firstWeek = date.isBefore(LocalDate.parse("2026-09-28"));
            fixture.day(date, 2000, 4, firstWeek ? "150" : "130")
                    .setFoodGroupCalories("GRAINS=800;PROTEIN=600;VEG=400;SWEETS=200;DRINKS=150");
            fixture.meal(date, "BREAKFAST", 400);
            fixture.meal(date, "LUNCH", 600);
            fixture.meal(date, "DINNER", 800);
            fixture.meal(date, "SNACK", 200);
        }
        fixture.workout(LocalDate.parse("2026-09-29"), 1, 300);
        fixture.goal("PROTEIN", "140", null, "2026-10-01T00:00:00Z");
        fixture.wire();
    }

    @Test
    void heroComparesWithThePeriodBeforeAndUsesTheEnergyWeek() {
        twoPeriods();

        ProgressFoodResponse response = fixture.foodService(ProgressFixture.clockAt(SATURDAY_NOON))
                .getFood(fixture.userId, ProgressWindow.of(FROM, TO), "UTC");

        assertThat(response.previous().from()).isEqualTo(LocalDate.parse("2026-09-07"));
        assertThat(response.hero().eatenPerDay().value()).isEqualTo(2000);
        assertThat(response.hero().eatenPerDay().previous()).isEqualTo(2000);
        assertThat(response.hero().proteinPerDay().value()).isEqualByComparingTo("141.7");
        assertThat(response.hero().proteinPerDay().previous()).isEqualByComparingTo("150.0");
        assertThat(response.hero().daysLogged().value()).isEqualTo(12);
        assertThat(response.hero().daysLogged().previous()).isEqualTo(7);
        assertThat(response.hero().daysLogged().days()).isEqualTo(14);
        assertThat(response.habits().loggedDays()).isEqualTo(12);

        AnalyticsEnergyWeeklyResponse thisWeek = response.energy().thisWeek();
        assertThat(thisWeek.weekStart()).isEqualTo(LocalDate.parse("2026-09-28"));
        assertThat(response.hero().balancePerDay().value()).isEqualTo(thisWeek.average().balance());
        assertThat(response.hero().balancePerDay().daysLogged()).isEqualTo(thisWeek.daysLogged());
        assertThat(response.energy().weeklyBalance()).hasSize(2);
        assertThat(response.energy().weeklyBalance().get(1).current()).isTrue();
        // The weekly bar of the current week is the energy card's own average.
        assertThat(response.energy().weeklyBalance().get(1).balance()).isEqualTo(thisWeek.average().balance());
        assertThat(response.energy().weeklyBalance().get(1).daysLogged()).isEqualTo(thisWeek.daysLogged());
        // resting 1750 x 1.2 = 2100, so a 2000 kcal day is 100 under.
        assertThat(response.energy().weeklyBalance().get(0).balance()).isEqualTo(-100);
    }

    @Test
    void macrosPlateAndMealsWithTheirHighlights() {
        twoPeriods();

        ProgressFoodResponse response = fixture.foodService(ProgressFixture.clockAt(SATURDAY_NOON))
                .getFood(fixture.userId, ProgressWindow.of(FROM, TO), "UTC");

        assertThat(response.macros().protein().firstWeek()).isEqualByComparingTo("150.0");
        assertThat(response.macros().protein().thisWeek()).isEqualByComparingTo("130.0");
        assertThat(response.macros().protein().target()).isEqualByComparingTo("140");
        assertThat(response.macros().protein().series()).hasSize(2);
        assertThat(response.macros().summary().get(0).text()).isEqualTo("Protein 150 → 130 g a day");
        assertThat(response.macros().summary().get(0).trend()).isEqualTo("DOWN");

        assertThat(response.plate().basis()).isEqualTo("CALORIES");
        assertThat(response.plate().current()).extracting(PlateGroup::group)
                .containsExactly("VEG", "GRAINS", "PROTEIN", "SWEETS");
        assertThat(sum(response.plate().current())).isEqualByComparingTo("100.0");
        assertThat(sum(response.plate().previous())).isEqualByComparingTo("100.0");
        // Vegetables went from 5% to 20% of the calories.
        assertThat(response.plate().changes().get(0).group()).isEqualTo("VEG");
        assertThat(response.plate().changes().get(0).deltaPoints()).isEqualByComparingTo("15.0");

        assertThat(response.meals().share()).extracting(MealShare::mealType)
                .containsExactly("BREAKFAST", "LUNCH", "DINNER", "SNACK");
        assertThat(response.meals().share()).extracting(MealShare::sharePct).containsExactly(20, 30, 40, 10);
        assertThat(response.meals().share()).extracting(MealShare::deltaPoints).containsExactly(-5, 5, 15, -15);
        assertThat(response.meals().share()).extracting(MealShare::kcalPerDay).containsExactly(400, 600, 800, 200);

        assertThat(response.highlights()).extracting(ProgressHighlight::code)
                .containsExactly("PROTEIN_DRIFT", "PLATE_SHIFT", "LARGEST_MEAL");
        assertThat(response.highlights().get(2).title()).isEqualTo("Dinner is your largest meal, 40% of the day.");
    }

    /** Four full weeks to Sunday 4 Oct at 157 g of protein, then Monday 5 Oct with breakfast only. */
    private void fourWeeksAndABreakfast() {
        fixture.profile("2026-08-01T00:00:00Z", 1750);
        for (LocalDate date = LocalDate.parse("2026-09-07"); !date.isAfter(LocalDate.parse("2026-10-04"));
             date = date.plusDays(1)) {
            fixture.day(date, 2032, 3, "157");
            fixture.meal(date, "BREAKFAST", 500);
            fixture.meal(date, "LUNCH", 700);
            fixture.meal(date, "DINNER", 832);
        }
        fixture.day(MONDAY, 476, 1, "29");
        fixture.meal(MONDAY, "BREAKFAST", 476);
        fixture.goal("PROTEIN", "140", null, "2026-10-01T00:00:00Z");
        fixture.goal("CALORIES", "2050", null, "2026-10-01T00:00:00Z");
        fixture.wire();
    }

    @Test
    void todayIsNeverAWeekAndTheSeriesEndsAtTheLastFullWeek() {
        fourWeeksAndABreakfast();

        ProgressFoodResponse response = fixture.foodService(ProgressFixture.clockAt(MONDAY_MORNING))
                .getFood(fixture.userId, ProgressWindow.of(LocalDate.parse("2026-09-08"), MONDAY), "UTC");

        assertThat(response.macros().protein().series()).extracting(ProgressFoodResponse.WeekValue::weekStart)
                .last().isEqualTo(LocalDate.parse("2026-09-28"));
        assertThat(response.macros().protein().series()).hasSize(4);
        assertThat(response.macros().protein().thisWeek()).isEqualByComparingTo("157.0");
        assertThat(response.macros().summary().get(0).text()).isEqualTo("Protein 157 → 157 g a day");
        assertThat(response.highlights()).extracting(ProgressHighlight::title)
                .noneMatch(title -> title.contains("29") || title.contains("476"));
        assertThat(response.highlights()).extracting(ProgressHighlight::code).doesNotContain("PROTEIN_DRIFT");

        assertThat(response.hero().eatenPerDay().value()).isEqualTo(2032);
        assertThat(response.hero().proteinPerDay().value()).isEqualByComparingTo("157.0");
        assertThat(response.hero().daysLogged().value()).isEqualTo(27);
        assertThat(response.hero().targetKcal()).isEqualTo(2050);
        assertThat(response.habits().loggedDays()).isEqualTo(27);
        assertThat(response.meals().share()).extracting(MealShare::kcalPerDay).containsExactly(500, 700, 832, 0);

        assertThat(response.energy().weeklyBalance()).extracting(ProgressFoodResponse.WeekBalance::weekStart)
                .last().isEqualTo(LocalDate.parse("2026-09-28"));
        assertThat(response.energy().weeklyBalance()).noneMatch(ProgressFoodResponse.WeekBalance::current);

        ProgressFoodResponse.CurrentWeek week = response.currentWeek();
        assertThat(week.weekStart()).isEqualTo(MONDAY);
        assertThat(week.included()).isFalse();
        assertThat(week.daysLogged()).isZero();
        assertThat(week.eatenPerDayKcal()).isNull();
        assertThat(week.eatenTodaySoFarKcal()).isEqualTo(476);
        assertThat(week.proteinTodaySoFarGrams()).isEqualByComparingTo("29.0");
    }

    @Test
    void theCurrentWeekJoinsTheSeriesWithTwoLoggedDaysBeforeToday() {
        fourWeeksAndABreakfast();
        fixture.day(LocalDate.parse("2026-10-06"), 2000, 3, "150");
        fixture.day(LocalDate.parse("2026-10-07"), 300, 1, "20");

        // Wednesday 7 Oct: Monday and Tuesday are over, Wednesday is still going.
        ProgressFoodResponse response = fixture.foodService(ProgressFixture.clockAt("2026-10-07T09:00:00Z"))
                .getFood(fixture.userId, ProgressWindow.of(LocalDate.parse("2026-09-10"),
                        LocalDate.parse("2026-10-07")), "UTC");

        assertThat(response.macros().protein().series()).extracting(ProgressFoodResponse.WeekValue::weekStart)
                .last().isEqualTo(MONDAY);
        // Monday 29 g and Tuesday 150 g; Wednesday's 20 g so far is left out.
        assertThat(response.macros().protein().thisWeek()).isEqualByComparingTo("89.5");
        assertThat(response.currentWeek().included()).isTrue();
        assertThat(response.currentWeek().daysLogged()).isEqualTo(2);
        assertThat(response.currentWeek().eatenPerDayKcal()).isEqualTo(1238);
        assertThat(response.currentWeek().eatenTodaySoFarKcal()).isEqualTo(300);
    }

    @Test
    void aPeriodThatEndsBeforeTodayHasNoTodayFigures() {
        fourWeeksAndABreakfast();

        ProgressFoodResponse response = fixture.foodService(ProgressFixture.clockAt(MONDAY_MORNING))
                .getFood(fixture.userId, ProgressWindow.of(LocalDate.parse("2026-09-07"), TO), "UTC");

        assertThat(response.macros().protein().series()).hasSize(4);
        assertThat(response.currentWeek().included()).isFalse();
        assertThat(response.currentWeek().eatenTodaySoFarKcal()).isNull();
        assertThat(response.hero().daysLogged().value()).isEqualTo(28);
    }

    @Test
    void anEmptyPeriodHasNoFiguresAndNoHighlights() {
        fixture.wire();

        ProgressFoodResponse response = fixture.foodService(ProgressFixture.clockAt(SATURDAY_NOON))
                .getFood(fixture.userId, ProgressWindow.of(FROM, TO), null);

        assertThat(response.hero().eatenPerDay().value()).isNull();
        assertThat(response.hero().daysLogged().value()).isZero();
        assertThat(response.plate().current()).isEmpty();
        assertThat(response.plate().changes()).isEmpty();
        assertThat(response.meals().share()).isEmpty();
        assertThat(response.macros().summary()).isEmpty();
        assertThat(response.highlights()).isEmpty();
    }

    @Test
    void sharesAlwaysAddUpToExactly100() {
        assertThat(ProgressFoodService.largestRemainder(List.of(1L, 1L, 1L), 1000)).containsExactly(334, 333, 333);
        assertThat(ProgressFoodService.largestRemainder(List.of(1L, 1L, 1L), 100)).containsExactly(34, 33, 33);
        assertThat(ProgressFoodService.largestRemainder(List.of(0L, 0L), 100)).containsExactly(0, 0);
        assertThat(ProgressFoodService.largestRemainder(List.of(7L, 993L), 100)).containsExactly(1, 99);

        AnalyticsUserDailyNutritionEntity row = fixture.day(FROM, 1000, 3, "50");
        row.setFoodGroupCalories("VEG=333;FRUIT=333;GRAINS=334;DRINKS=200;BAD;X=y");
        List<PlateGroup> plate = ProgressFoodService.plate(List.of(row));
        assertThat(plate).extracting(PlateGroup::group).containsExactly("VEG", "FRUIT", "GRAINS");
        assertThat(plate).extracting(group -> group.share().toPlainString()).containsExactly("33.3", "33.3", "33.4");
        assertThat(sum(plate)).isEqualByComparingTo("100.0");
    }

    @Test
    void trendWordsUseTheEightPercentRule() {
        assertThat(ProgressFoodService.trend(new BigDecimal("100"), new BigDecimal("108"))).isEqualTo("UP");
        assertThat(ProgressFoodService.trend(new BigDecimal("100"), new BigDecimal("107.9"))).isEqualTo("STEADY");
        assertThat(ProgressFoodService.trend(new BigDecimal("100"), new BigDecimal("92"))).isEqualTo("DOWN");
        assertThat(ProgressFoodService.trend(null, new BigDecimal("92"))).isEqualTo("STEADY");
    }

    private static BigDecimal sum(List<PlateGroup> plate) {
        return plate.stream().map(PlateGroup::share).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
