package com.fitsync.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.analytics.dto.AnalyticsEnergyWeeklyResponse;
import com.fitsync.analytics.dto.AnalyticsEnergyWeeklyResponse.Day;
import com.fitsync.analytics.entity.AnalyticsMealContributionEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyBodyEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyWorkoutEntity;
import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import com.fitsync.analytics.entity.AnalyticsUserEnergyProfileEntity;
import com.fitsync.analytics.repository.AnalyticsMealContributionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserEnergyProfileRepository;
import com.fitsync.web.error.BadRequestException;
import org.junit.jupiter.api.Test;

class AnalyticsEnergyServiceTests {

    private static final LocalDate MONDAY = LocalDate.parse("2026-09-28");
    private static final String UTC = "UTC";

    private final UUID userId = UUID.randomUUID();
    private final AnalyticsUserDashboardSnapshotRepository snapshotRepository =
            mock(AnalyticsUserDashboardSnapshotRepository.class);
    private final AnalyticsUserDailyNutritionRepository nutritionRepository =
            mock(AnalyticsUserDailyNutritionRepository.class);
    private final AnalyticsUserDailyWorkoutRepository workoutRepository =
            mock(AnalyticsUserDailyWorkoutRepository.class);
    private final AnalyticsUserDailyBodyRepository bodyRepository = mock(AnalyticsUserDailyBodyRepository.class);
    private final AnalyticsMealContributionRepository mealRepository =
            mock(AnalyticsMealContributionRepository.class);
    private final AnalyticsUserEnergyProfileRepository energyRepository =
            mock(AnalyticsUserEnergyProfileRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-02T12:00:00Z"), ZoneOffset.UTC);
    private final AnalyticsEnergyService service = new AnalyticsEnergyService(
            snapshotRepository, nutritionRepository, workoutRepository, bodyRepository, mealRepository,
            energyRepository, clock);

    private final List<AnalyticsUserDailyNutritionEntity> nutritionRows = new ArrayList<>();
    private final List<AnalyticsUserDailyWorkoutEntity> workoutRows = new ArrayList<>();
    private final List<AnalyticsUserDailyBodyEntity> bodyRows = new ArrayList<>();
    private final List<AnalyticsMealContributionEntity> mealRows = new ArrayList<>();
    private final List<AnalyticsUserEnergyProfileEntity> profiles = new ArrayList<>();

    @Test
    void rejectsAWeekStartThatIsNotAMondayAndAnInvalidTimezone() {
        assertThatThrownBy(() -> service.getWeekly(userId, MONDAY.plusDays(1), UTC))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Monday");
        assertThatThrownBy(() -> service.getWeekly(userId, MONDAY, "Mars/Olympus"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("timezone");
    }

    @Test
    void defaultsToThisWeekInTheGivenZone() {
        // 2026-10-02 12:00 UTC is already Friday evening in Singapore, still the same ISO week.
        wire();
        assertThat(service.getWeekly(userId, null, "Asia/Singapore").weekStart()).isEqualTo(MONDAY);
        // Sunday 22:00 UTC is Monday 06:00 in Singapore, so the zone moves the week.
        Clock sunday = Clock.fixed(Instant.parse("2026-10-04T22:00:00Z"), ZoneOffset.UTC);
        AnalyticsEnergyService shifted = new AnalyticsEnergyService(snapshotRepository, nutritionRepository,
                workoutRepository, bodyRepository, mealRepository, energyRepository, sunday);
        assertThat(shifted.getWeekly(userId, null, "Asia/Singapore").weekStart()).isEqualTo(MONDAY.plusWeeks(1));
        assertThat(shifted.getWeekly(userId, null, UTC).weekStart()).isEqualTo(MONDAY);
    }

    @Test
    void usesTheStoredUserZoneWhenNoTimezoneIsGiven() {
        wire();
        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setTimezone("Asia/Singapore");
        when(snapshotRepository.findById(userId)).thenReturn(Optional.of(snapshot));
        Clock sunday = Clock.fixed(Instant.parse("2026-10-04T22:00:00Z"), ZoneOffset.UTC);
        AnalyticsEnergyService shifted = new AnalyticsEnergyService(snapshotRepository, nutritionRepository,
                workoutRepository, bodyRepository, mealRepository, energyRepository, sunday);

        assertThat(shifted.getWeekly(userId, null, null).weekStart()).isEqualTo(MONDAY.plusWeeks(1));
    }

    @Test
    void reportsNoProfileWithoutABmrButStillReturnsWhatIsKnown() {
        loggedDay(MONDAY, 2000, 2);
        loggedDay(MONDAY.plusDays(1), 1800, 3);
        loggedDay(MONDAY.plusDays(2), 2200, 3);
        wire();

        AnalyticsEnergyWeeklyResponse response = service.getWeekly(userId, MONDAY, UTC);

        assertThat(response.status()).isEqualTo("NO_PROFILE");
        assertThat(response.bmrKcal()).isNull();
        assertThat(response.daysLogged()).isEqualTo(3);
        assertThat(response.average().eaten()).isEqualTo(2000);
        assertThat(response.average().resting()).isNull();
        assertThat(response.average().balance()).isNull();
        assertThat(response.weeklyKgEquivalent()).isNull();
        assertThat(response.days()).hasSize(7);
        assertThat(response.days().get(0).eaten()).isEqualTo(2000);
        assertThat(response.days().get(0).balance()).isNull();
        assertThat(response.days().get(0).runningTotal()).isNull();
    }

    @Test
    void reportsTooFewDaysUnderThreeLoggedDays() {
        profile(Instant.parse("2026-09-01T00:00:00Z"), 1700, false);
        loggedDay(MONDAY, 2000, 2);
        loggedDay(MONDAY.plusDays(3), 2100, 3);
        wire();

        AnalyticsEnergyWeeklyResponse response = service.getWeekly(userId, MONDAY, UTC);

        assertThat(response.status()).isEqualTo("TOO_FEW_DAYS");
        assertThat(response.daysLogged()).isEqualTo(2);
        assertThat(response.bmrKcal()).isEqualTo(1700);
        assertThat(response.weeklyKgEquivalent()).isNull();
        assertThat(response.average()).isNotNull();
        assertThat(response.days()).hasSize(7);
    }

    @Test
    void computesAnOkWeekWithRunningTotalAnUnloggedDayAndAnEstimatedWorkoutDay() {
        profile(Instant.parse("2026-09-01T00:00:00Z"), 1700, true);
        // resting = 1700 x 1.2 = 2040
        loggedDay(MONDAY, 2000, 2);                       // balance -40
        loggedDay(MONDAY.plusDays(1), 2500, 3);           // balance +460
        workoutDay(MONDAY.plusDays(1), 300, 300);         // estimated: used 2340, balance +160
        // Wednesday is not logged but has a workout that must not count
        workoutDay(MONDAY.plusDays(2), 500, 0);
        loggedDay(MONDAY.plusDays(3), 2040, 3);           // balance 0
        wire();

        AnalyticsEnergyWeeklyResponse response = service.getWeekly(userId, MONDAY, UTC);

        assertThat(response.status()).isEqualTo("OK");
        assertThat(response.daysLogged()).isEqualTo(3);
        assertThat(response.bmrKcal()).isEqualTo(1700);
        assertThat(response.bmrEstimated()).isTrue();
        assertThat(response.restingFactor()).isEqualTo(1.2);
        assertThat(response.weekEnd()).isEqualTo(MONDAY.plusDays(6));

        List<Day> days = response.days();
        assertThat(days.get(0)).satisfies(day -> {
            assertThat(day.logged()).isTrue();
            assertThat(day.eaten()).isEqualTo(2000);
            assertThat(day.resting()).isEqualTo(2040);
            assertThat(day.workouts()).isZero();
            assertThat(day.workoutsEstimated()).isFalse();
            assertThat(day.balance()).isEqualTo(-40);
            assertThat(day.runningTotal()).isEqualTo(-40);
        });
        assertThat(days.get(1)).satisfies(day -> {
            assertThat(day.workouts()).isEqualTo(300);
            assertThat(day.workoutsEstimated()).isTrue();
            assertThat(day.balance()).isEqualTo(160);
            assertThat(day.runningTotal()).isEqualTo(120);
        });
        assertThat(days.get(2)).satisfies(day -> {
            assertThat(day.logged()).isFalse();
            assertThat(day.eaten()).isNull();
            assertThat(day.balance()).isNull();
            assertThat(day.runningTotal()).isNull();
            assertThat(day.workouts()).isEqualTo(500);
        });
        assertThat(days.get(3).balance()).isZero();
        assertThat(days.get(3).runningTotal()).isEqualTo(120);

        // Averages are per logged day: eaten (2000 + 2500 + 2040) / 3 = 2180, workouts 300 / 3 = 100.
        assertThat(response.average().eaten()).isEqualTo(2180);
        assertThat(response.average().resting()).isEqualTo(2040);
        assertThat(response.average().workouts()).isEqualTo(100);
        assertThat(response.average().used()).isEqualTo(2140);
        assertThat(response.average().balance()).isEqualTo(40);
        // 40 x 7 / 7700 = 0.036 kg, rounded to 0.01
        assertThat(response.weeklyKgEquivalent()).isEqualTo(0.04);
    }

    @Test
    void todayIsShownButNeverCounted() {
        profile(Instant.parse("2026-09-01T00:00:00Z"), 1700, false);
        // The clock says Friday 2026-10-02. Three past days are logged, Friday only has breakfast and lunch so far.
        loggedDay(MONDAY, 2040, 2);
        loggedDay(MONDAY.plusDays(1), 2040, 2);
        loggedDay(MONDAY.plusDays(2), 2040, 2);
        loggedDay(MONDAY.plusDays(4), 932, 2);
        workoutDay(MONDAY.plusDays(4), 400, 0);
        meal(MONDAY.plusDays(2), "LUNCH", 2040);
        meal(MONDAY.plusDays(2), "DINNER", 0);
        meal(MONDAY.plusDays(4), "BREAKFAST", 932);
        wire();

        AnalyticsEnergyWeeklyResponse response = service.getWeekly(userId, MONDAY, UTC);

        Day friday = response.days().get(4);
        assertThat(friday.today()).isTrue();
        assertThat(friday.eaten()).isEqualTo(932);
        assertThat(friday.workouts()).isEqualTo(400);
        assertThat(friday.balance()).isNull();
        assertThat(friday.runningTotal()).isNull();
        assertThat(response.days().stream().filter(Day::today)).hasSize(1);
        assertThat(response.daysLogged()).isEqualTo(3);
        assertThat(response.status()).isEqualTo("OK");
        assertThat(response.average().eaten()).isEqualTo(2040);
        assertThat(response.average().workouts()).isZero();
        assertThat(response.average().balance()).isZero();
        assertThat(response.mealSplit().breakfast()).isZero();
        assertThat(response.mealSplit().lunch()).isEqualTo(680);
        assertThat(response.days().get(2).runningTotal()).isZero();
        assertThat(response.weeks().get(7).expectedKgChange()).isEqualTo(0.0);
    }

    @Test
    void aDayWithOneMealIsPartlyLoggedAndCountsForNothing() {
        profile(Instant.parse("2026-09-01T00:00:00Z"), 1700, false);
        // resting 2040. Monday, Tuesday and Thursday have two meals. Wednesday has only a snack, so it is partly logged.
        loggedDay(MONDAY, 2040, 2);
        meal(MONDAY, "BREAKFAST", 1000);
        meal(MONDAY, "DINNER", 1040);
        loggedDay(MONDAY.plusDays(1), 2040, 2);
        meal(MONDAY.plusDays(1), "LUNCH", 1000);
        meal(MONDAY.plusDays(1), "SNACK", 1040);
        loggedDay(MONDAY.plusDays(2), 932, 1);
        meal(MONDAY.plusDays(2), "SNACK", 932);
        loggedDay(MONDAY.plusDays(3), 2040, 2);
        meal(MONDAY.plusDays(3), "BREAKFAST", 1000);
        meal(MONDAY.plusDays(3), "SNACK", 1040);
        wire();

        AnalyticsEnergyWeeklyResponse response = service.getWeekly(userId, MONDAY, UTC);

        Day wednesday = response.days().get(2);
        assertThat(wednesday.partlyLogged()).isTrue();
        assertThat(wednesday.logged()).isFalse();
        assertThat(wednesday.eaten()).isEqualTo(932);
        assertThat(wednesday.balance()).isNull();
        assertThat(wednesday.runningTotal()).isNull();
        assertThat(response.days().get(0).partlyLogged()).isFalse();
        assertThat(response.days().get(0).logged()).isTrue();
        assertThat(response.daysLogged()).isEqualTo(3);
        assertThat(response.status()).isEqualTo("OK");
        // Averages, split and the running total ignore Wednesday: (2040 x 3) / 3.
        assertThat(response.average().eaten()).isEqualTo(2040);
        assertThat(response.average().balance()).isZero();
        assertThat(response.mealSplit().snack()).isEqualTo(693);
        assertThat(response.days().get(3).runningTotal()).isZero();
        assertThat(response.weeks().get(7).expectedKgChange()).isEqualTo(0.0);
    }

    @Test
    void aPartlyLoggedDayDoesNotLiftAWeekOverTheThreeDayRule() {
        profile(Instant.parse("2026-09-01T00:00:00Z"), 1700, false);
        loggedDay(MONDAY, 2040, 2);
        loggedDay(MONDAY.plusDays(1), 2040, 2);
        loggedDay(MONDAY.plusDays(2), 900, 1);
        meal(MONDAY.plusDays(2), "LUNCH", 900);
        wire();

        AnalyticsEnergyWeeklyResponse response = service.getWeekly(userId, MONDAY, UTC);

        assertThat(response.daysLogged()).isEqualTo(2);
        assertThat(response.status()).isEqualTo("TOO_FEW_DAYS");
        assertThat(response.days().get(2).partlyLogged()).isTrue();
    }

    @Test
    void mealsOfOneDayAreCountedByDistinctMealIdNotByDailyRowCount() {
        profile(Instant.parse("2026-09-01T00:00:00Z"), 1700, false);
        // The daily row says 3 meals, but only one meal contribution exists for the day.
        loggedDay(MONDAY, 800, 3);
        meal(MONDAY, "LUNCH", 800);
        wire();

        Day monday = service.getWeekly(userId, MONDAY, UTC).days().get(0);

        assertThat(monday.partlyLogged()).isTrue();
        assertThat(monday.logged()).isFalse();
    }

    @Test
    void todayDoesNotLiftAWeekOverTheThreeDayRuleAndPastWeeksHaveNoToday() {
        profile(Instant.parse("2026-09-01T00:00:00Z"), 1700, false);
        loggedDay(MONDAY, 2040, 2);
        loggedDay(MONDAY.plusDays(1), 2040, 2);
        loggedDay(MONDAY.plusDays(4), 2040, 3);
        wire();

        assertThat(service.getWeekly(userId, MONDAY, UTC).status()).isEqualTo("TOO_FEW_DAYS");
        assertThat(service.getWeekly(userId, MONDAY, UTC).daysLogged()).isEqualTo(2);
        assertThat(service.getWeekly(userId, MONDAY.minusWeeks(1), UTC).days()).noneMatch(Day::today);
    }

    @Test
    void picksTheBmrThatWasNewestAtTheEndOfEachDayAndTheEarliestBeforeAnyComputation() {
        // First computation on Wednesday 10:00, second on Friday 08:00.
        profile(Instant.parse("2026-09-30T10:00:00Z"), 1600, false);
        profile(Instant.parse("2026-10-02T08:00:00Z"), 1800, false);
        for (int d = 0; d < 7; d++) {
            loggedDay(MONDAY.plusDays(d), 2000, 2);
        }
        wire();

        AnalyticsEnergyWeeklyResponse response = service.getWeekly(userId, MONDAY, UTC);

        // Monday and Tuesday lie before every computation, so they use the earliest one.
        assertThat(response.days().get(0).resting()).isEqualTo(1920);
        assertThat(response.days().get(1).resting()).isEqualTo(1920);
        // Wednesday ends after the first computation.
        assertThat(response.days().get(2).resting()).isEqualTo(1920);
        assertThat(response.days().get(3).resting()).isEqualTo(1920);
        // Friday ends after the second one and keeps it until the end of the week.
        assertThat(response.days().get(4).resting()).isEqualTo(2160);
        assertThat(response.days().get(6).resting()).isEqualTo(2160);
        assertThat(response.bmrKcal()).isEqualTo(1800);
    }

    @Test
    void anOlderPastWeekUsesTheComputationThatWasValidThen() {
        profile(Instant.parse("2026-08-01T00:00:00Z"), 1500, false);
        profile(Instant.parse("2026-10-01T00:00:00Z"), 1900, false);
        LocalDate pastMonday = MONDAY.minusWeeks(3);
        for (int d = 0; d < 3; d++) {
            loggedDay(pastMonday.plusDays(d), 2000, 2);
        }
        wire();

        AnalyticsEnergyWeeklyResponse response = service.getWeekly(userId, pastMonday, UTC);

        assertThat(response.bmrKcal()).isEqualTo(1500);
        assertThat(response.days().get(0).resting()).isEqualTo(1800);
    }

    @Test
    void splitsMealsByTypeOnLoggedDaysAndCountsMissingTypesAsOther() {
        profile(Instant.parse("2026-09-01T00:00:00Z"), 1700, false);
        loggedDay(MONDAY, 1600, 5);
        loggedDay(MONDAY.plusDays(1), 800, 2);
        meal(MONDAY, "BREAKFAST", 400);
        meal(MONDAY, "lunch", 500);
        meal(MONDAY, "SUPPER", 300);
        meal(MONDAY, "SNACK", 100);
        meal(MONDAY, null, 300);
        meal(MONDAY.plusDays(1), "BREAKFAST", 200);
        meal(MONDAY.plusDays(1), "OTHER", 600);
        // A meal on a day the daily row does not call logged is left out.
        meal(MONDAY.plusDays(2), "DINNER", 999);
        wire();

        AnalyticsEnergyWeeklyResponse response = service.getWeekly(userId, MONDAY, UTC);

        assertThat(response.mealSplit().breakfast()).isEqualTo(300);
        assertThat(response.mealSplit().lunch()).isEqualTo(250);
        assertThat(response.mealSplit().dinner()).isEqualTo(150);
        assertThat(response.mealSplit().snack()).isEqualTo(50);
        assertThat(response.mealSplit().other()).isEqualTo(450);
    }

    @Test
    void returnsEightWeeksWithExpectedCumulativeAndActualFromTheFirstWeighIn() {
        profile(Instant.parse("2026-01-01T00:00:00Z"), 1700, false);
        LocalDate first = MONDAY.minusWeeks(7);
        // Week 0 has no weigh-in, week 1 starts the baseline at 80.0, week 2 has none, week 3 averages 79.0.
        weight(first.plusWeeks(1).plusDays(1), "80.2");
        weight(first.plusWeeks(1).plusDays(4), "79.8");
        weight(first.plusWeeks(3).plusDays(2), "79.0");
        // Week 0: one logged day at +460 (2500 - 2040); week 2: one logged day at -540 (1500 - 2040).
        loggedDay(first.plusDays(1), 2500, 3);
        loggedDay(first.plusWeeks(2).plusDays(1), 1500, 3);
        wire();

        AnalyticsEnergyWeeklyResponse response = service.getWeekly(userId, MONDAY, UTC);

        assertThat(response.weeks()).hasSize(8);
        assertThat(response.weeks().get(0).weekStart()).isEqualTo(first);
        assertThat(response.weeks().get(7).weekStart()).isEqualTo(MONDAY);
        // 460 / 7700 = 0.06, then (460 - 540) / 7700 = -0.01.
        assertThat(response.weeks().get(0).expectedKgChange()).isEqualTo(0.06);
        assertThat(response.weeks().get(1).expectedKgChange()).isEqualTo(0.06);
        assertThat(response.weeks().get(2).expectedKgChange()).isEqualTo(-0.01);
        assertThat(response.weeks().get(7).expectedKgChange()).isEqualTo(-0.01);
        assertThat(response.weeks().get(0).actualKgChange()).isNull();
        assertThat(response.weeks().get(1).actualKgChange()).isEqualTo(0.0);
        assertThat(response.weeks().get(2).actualKgChange()).isNull();
        assertThat(response.weeks().get(3).actualKgChange()).isEqualTo(-1.0);
        assertThat(response.weeks().get(4).actualKgChange()).isNull();
    }

    @Test
    void weeksWithoutAnyLoggedDayOrWeighInStayNull() {
        profile(Instant.parse("2026-01-01T00:00:00Z"), 1700, false);
        wire();

        AnalyticsEnergyWeeklyResponse response = service.getWeekly(userId, MONDAY, UTC);

        assertThat(response.status()).isEqualTo("TOO_FEW_DAYS");
        assertThat(response.daysLogged()).isZero();
        assertThat(response.average()).isNull();
        assertThat(response.mealSplit()).isNull();
        assertThat(response.weeks()).allSatisfy(week -> {
            assertThat(week.expectedKgChange()).isNull();
            assertThat(week.actualKgChange()).isNull();
        });
    }

    // ---- fixtures --------------------------------------------------------------------------------

    private void loggedDay(LocalDate date, int calories, int meals) {
        AnalyticsUserDailyNutritionEntity row = new AnalyticsUserDailyNutritionEntity();
        row.setUserId(userId);
        row.setActivityDate(date);
        row.setMealCount(meals);
        row.setTotalCaloriesConsumed(calories);
        nutritionRows.add(row);
    }

    private void workoutDay(LocalDate date, int calories, int estimatedCalories) {
        AnalyticsUserDailyWorkoutEntity row = new AnalyticsUserDailyWorkoutEntity();
        row.setUserId(userId);
        row.setActivityDate(date);
        row.setTotalCaloriesBurned(calories);
        row.setEstimatedCaloriesBurned(estimatedCalories);
        workoutRows.add(row);
    }

    private void weight(LocalDate date, String kg) {
        AnalyticsUserDailyBodyEntity row = new AnalyticsUserDailyBodyEntity();
        row.setUserId(userId);
        row.setMetricDate(date);
        row.setWeightKg(new BigDecimal(kg));
        bodyRows.add(row);
    }

    private void meal(LocalDate date, String type, int calories) {
        AnalyticsMealContributionEntity row = new AnalyticsMealContributionEntity();
        row.setMealId(UUID.randomUUID());
        row.setUserId(userId);
        row.setActivityDate(date);
        row.setMealType(type);
        row.setTotalCalories(calories);
        mealRows.add(row);
    }

    private void profile(Instant computedAt, int bmr, boolean estimated) {
        AnalyticsUserEnergyProfileEntity row = new AnalyticsUserEnergyProfileEntity();
        row.setEventId(UUID.randomUUID());
        row.setUserId(userId);
        row.setBmrKcal(bmr);
        row.setEstimated(estimated);
        row.setComputedAt(computedAt);
        profiles.add(row);
    }

    private void wire() {
        when(nutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(eq(userId), any(), any()))
                .thenAnswer(call -> inRange(nutritionRows, call.getArgument(1), call.getArgument(2),
                        AnalyticsUserDailyNutritionEntity::getActivityDate));
        when(workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(eq(userId), any(), any()))
                .thenAnswer(call -> inRange(workoutRows, call.getArgument(1), call.getArgument(2),
                        AnalyticsUserDailyWorkoutEntity::getActivityDate));
        when(bodyRepository.findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(eq(userId), any(), any()))
                .thenAnswer(call -> inRange(bodyRows, call.getArgument(1), call.getArgument(2),
                        AnalyticsUserDailyBodyEntity::getMetricDate));
        when(mealRepository.findByUserIdAndActivityDateBetween(eq(userId), any(), any()))
                .thenAnswer(call -> inRange(mealRows, call.getArgument(1), call.getArgument(2),
                        AnalyticsMealContributionEntity::getActivityDate));
        List<AnalyticsUserEnergyProfileEntity> ordered = profiles.stream()
                .sorted(java.util.Comparator.comparing(AnalyticsUserEnergyProfileEntity::getComputedAt)).toList();
        when(energyRepository.findFirstByUserIdAndComputedAtBeforeOrderByComputedAtDesc(eq(userId), any()))
                .thenAnswer(call -> ordered.stream()
                        .filter(row -> row.getComputedAt().isBefore(call.<Instant>getArgument(1)))
                        .reduce((a, b) -> b));
        when(energyRepository.findByUserIdAndComputedAtGreaterThanEqualAndComputedAtBeforeOrderByComputedAtAsc(
                eq(userId), any(), any()))
                .thenAnswer(call -> ordered.stream()
                        .filter(row -> !row.getComputedAt().isBefore(call.<Instant>getArgument(1))
                                && row.getComputedAt().isBefore(call.<Instant>getArgument(2)))
                        .toList());
        when(energyRepository.findFirstByUserIdOrderByComputedAtAsc(userId))
                .thenReturn(ordered.stream().findFirst());
    }

    private static <T> List<T> inRange(List<T> rows, LocalDate from, LocalDate to,
                                       java.util.function.Function<T, LocalDate> dateOf) {
        return rows.stream()
                .filter(row -> !dateOf.apply(row).isBefore(from) && !dateOf.apply(row).isAfter(to))
                .sorted(java.util.Comparator.comparing(dateOf))
                .toList();
    }
}
