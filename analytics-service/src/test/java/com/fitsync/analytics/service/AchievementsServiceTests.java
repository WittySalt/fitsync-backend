package com.fitsync.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fitsync.analytics.dto.AnalyticsAchievementsResponse;
import com.fitsync.analytics.dto.AnalyticsAchievementsResponse.Badge;
import com.fitsync.analytics.entity.AnalyticsUserAchievementEntity;
import com.fitsync.analytics.entity.AnalyticsUserAchievementKey;
import com.fitsync.analytics.entity.AnalyticsUserCooksEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyWorkoutEntity;
import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import com.fitsync.analytics.entity.AnalyticsUserGoalProgressEntity;
import com.fitsync.analytics.entity.AnalyticsUserWorkoutRecordsEntity;
import com.fitsync.analytics.entity.AnalyticsWorkoutContributionEntity;
import com.fitsync.analytics.repository.AnalyticsUserAchievementRepository;
import com.fitsync.analytics.repository.AnalyticsUserCooksRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import com.fitsync.analytics.repository.AnalyticsUserWorkoutRecordsRepository;
import com.fitsync.analytics.repository.AnalyticsWorkoutContributionRepository;
import com.fitsync.analytics.security.CurrentUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Badges v2. The clock is Tuesday 6 Oct 2026, midday in Singapore; the current week runs from Monday
 * 5 Oct and the last full week from 28 Sept to 4 Oct.
 */
class AchievementsServiceTests {

    static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String NOW = "2026-10-06T04:00:00Z";

    final AnalyticsUserDashboardSnapshotRepository snapshotRepository =
            mock(AnalyticsUserDashboardSnapshotRepository.class);
    final AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
    final AnalyticsUserDailyNutritionRepository nutritionRepository =
            mock(AnalyticsUserDailyNutritionRepository.class);
    final AnalyticsUserGoalProgressRepository goalRepository = mock(AnalyticsUserGoalProgressRepository.class);
    final AnalyticsUserWorkoutRecordsRepository recordsRepository = mock(AnalyticsUserWorkoutRecordsRepository.class);
    final AnalyticsUserCooksRepository cooksRepository = mock(AnalyticsUserCooksRepository.class);
    final AnalyticsWorkoutContributionRepository contributionRepository =
            mock(AnalyticsWorkoutContributionRepository.class);
    final AnalyticsUserAchievementRepository achievementRepository = mock(AnalyticsUserAchievementRepository.class);
    /** The ledger table, kept in memory. */
    final Map<String, AnalyticsUserAchievementEntity> ledgerRows = new LinkedHashMap<>();
    final List<AnalyticsUserDailyWorkoutEntity> workoutDays = new ArrayList<>();
    final List<AnalyticsUserDailyNutritionEntity> nutritionDays = new ArrayList<>();

    AchievementsServiceTests() {
        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setUserId(USER_ID);
        snapshot.setTimezone("Asia/Singapore");
        when(snapshotRepository.findById(USER_ID)).thenReturn(Optional.of(snapshot));
        when(workoutRepository.findByUserIdOrderByActivityDateAsc(USER_ID)).thenAnswer(call -> List.copyOf(workoutDays));
        when(nutritionRepository.findByUserIdOrderByActivityDateAsc(USER_ID))
                .thenAnswer(call -> List.copyOf(nutritionDays));
        when(achievementRepository.findByUserId(USER_ID)).thenAnswer(call -> List.copyOf(ledgerRows.values()));
        when(achievementRepository.existsById(any(AnalyticsUserAchievementKey.class)))
                .thenAnswer(call -> ledgerRows.containsKey(call.<AnalyticsUserAchievementKey>getArgument(0).getCode()));
        when(achievementRepository.saveAndFlush(any(AnalyticsUserAchievementEntity.class))).thenAnswer(call -> {
            AnalyticsUserAchievementEntity row = call.getArgument(0);
            ledgerRows.put(row.getCode(), row);
            return row;
        });
    }

    AchievementsService service(String instant) {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new AchievementsService(snapshotRepository, workoutRepository, nutritionRepository,
                mock(AnalyticsUserDailyBodyRepository.class), goalRepository, recordsRepository, cooksRepository,
                contributionRepository, new AchievementLedger(achievementRepository, mock(PlatformTransactionManager.class)), objectMapper,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    AnalyticsAchievementsResponse read() {
        return service(NOW).getAchievements(principal());
    }

    static CurrentUserPrincipal principal() {
        return new CurrentUserPrincipal(USER_ID, "alice@example.com", "Alice", "USER");
    }

    // ---- fixtures ------------------------------------------------------------------------------------

    void trained(String date, int sessions) {
        AnalyticsUserDailyWorkoutEntity row = new AnalyticsUserDailyWorkoutEntity();
        row.setUserId(USER_ID);
        row.setActivityDate(LocalDate.parse(date));
        row.setCompletedWorkoutCount(sessions);
        row.setTotalWorkoutMinutes(45 * sessions);
        workoutDays.add(row);
    }

    /** Trains once a day on each of the days, from the first date on. */
    void trainedDays(String first, int days) {
        LocalDate day = LocalDate.parse(first);
        for (int i = 0; i < days; i++) {
            trained(day.plusDays(i).toString(), 1);
        }
    }

    void ate(String date, int meals, String protein, String proteinTarget) {
        AnalyticsUserDailyNutritionEntity row = new AnalyticsUserDailyNutritionEntity();
        row.setUserId(USER_ID);
        row.setActivityDate(LocalDate.parse(date));
        row.setMealCount(meals);
        row.setTotalCaloriesConsumed(meals == 0 ? 0 : 1800);
        row.setTotalProteinGrams(new BigDecimal(protein));
        row.setProteinTargetGrams(proteinTarget == null ? null : new BigDecimal(proteinTarget));
        nutritionDays.add(row);
    }

    /** A fast day is logged as a meal of its own with nothing eaten. */
    void fasted(String date) {
        AnalyticsUserDailyNutritionEntity row = new AnalyticsUserDailyNutritionEntity();
        row.setUserId(USER_ID);
        row.setActivityDate(LocalDate.parse(date));
        row.setMealCount(1);
        row.setTotalCaloriesConsumed(0);
        row.setTotalProteinGrams(BigDecimal.ZERO);
        nutritionDays.add(row);
    }

    void sessionsTarget(String target) {
        AnalyticsUserGoalProgressEntity goal = new AnalyticsUserGoalProgressEntity();
        goal.setGoalId(UUID.randomUUID());
        goal.setUserId(USER_ID);
        goal.setGoalType(GoalProgressEvaluator.WORKOUT_FREQUENCY);
        goal.setGoalStatus(GoalExpiryPolicy.ACTIVE_STATUS);
        goal.setTargetValue(new BigDecimal(target));
        when(goalRepository.findByUserIdAndGoalStatusAndGoalTypeInOrderByTargetDateAscGoalIdAsc(eq(USER_ID),
                eq(GoalExpiryPolicy.ACTIVE_STATUS), anySet())).thenReturn(List.of(goal));
    }

    static Badge badge(AnalyticsAchievementsResponse response, String code) {
        return response.themes().stream()
                .flatMap(theme -> theme.paths().stream())
                .flatMap(path -> path.nodes().stream())
                .filter(node -> node.code().equals(code))
                .findFirst()
                .orElseThrow();
    }

    static String progress(Badge badge) {
        return badge.progress() == null ? null
                : badge.progress().current().toPlainString() + "/" + badge.progress().target().toPlainString() + " "
                + badge.progress().unit();
    }

    // ---- the catalogue -------------------------------------------------------------------------------

    @Test
    void anEmptyAccountGetsTheWholeCatalogueWithFirstWorkoutAndFirstMealNext() {
        AnalyticsAchievementsResponse response = read();

        assertThat(response.earned()).isZero();
        assertThat(response.total()).isEqualTo(17);
        assertThat(response.themes())
                .extracting(AnalyticsAchievementsResponse.Theme::code, AnalyticsAchievementsResponse.Theme::name)
                .containsExactly(
                        tuple("SHOWING_UP", "Showing up"),
                        tuple("GETTING_STRONGER", "Getting stronger"),
                        tuple("EATING_WELL", "Eating well"),
                        tuple("IN_THE_KITCHEN", "In the kitchen"));
        assertThat(response.themes().stream().flatMap(theme -> theme.paths().stream())
                .map(path -> path.code() + ":" + path.nodes().stream().map(Badge::code).toList()))
                .containsExactly(
                        "SESSIONS:[FIRST_WORKOUT, WORKOUTS_10, WORKOUTS_50, WORKOUTS_100]",
                        "WEEKS:[FULL_WEEK, FOUR_FULL_WEEKS]",
                        "RECORDS:[FIRST_RECORD, FIVE_RECORDS]",
                        "LIFTS:[BODYWEIGHT_BENCH]",
                        "LOGGING:[FIRST_MEAL, SEVEN_DAYS_LOGGED, THIRTY_DAYS_LOGGED]",
                        "PROTEIN:[PROTEIN_WEEK, PROTEIN_MONTH]",
                        "COOKS:[FIRST_COOK, TEN_COOKS, FIFTY_COOKS]");
        assertThat(badge(response, "FIRST_WORKOUT").state()).isEqualTo("NEXT");
        assertThat(badge(response, "WORKOUTS_10").state()).isEqualTo("LATER");
        assertThat(progress(badge(response, "FIRST_WORKOUT"))).isEqualTo("0/1 sessions");
        assertThat(response.nextUp()).extracting(Badge::code).containsExactly("FIRST_WORKOUT", "FIRST_MEAL");
        assertThat(response.latest()).isNull();
        assertThat(response.earlier()).isEmpty();
        assertThat(response.pace().sessionsPerWeek()).isEqualByComparingTo("0");
        assertThat(ledgerRows).isEmpty();
    }

    @Test
    void figuresAnalyticsDoesNotHoldLeaveTheirBadgesWithoutProgress() {
        AnalyticsAchievementsResponse response = read();

        // No bench figure reaches analytics, no sessions target and no protein target are set.
        assertThat(badge(response, "BODYWEIGHT_BENCH").progress()).isNull();
        assertThat(badge(response, "FULL_WEEK").progress()).isNull();
        assertThat(badge(response, "FOUR_FULL_WEEKS").progress()).isNull();
        assertThat(badge(response, "PROTEIN_WEEK").progress()).isNull();
        assertThat(badge(response, "PROTEIN_MONTH").progress()).isNull();
    }

    // ---- showing up ----------------------------------------------------------------------------------

    @Test
    void sessionBadgesAreEarnedOnTheDayTheCountCrossesAndNameTheSession() {
        trained("2026-09-01", 1);
        trained("2026-09-02", 8);
        trained("2026-09-03", 2);
        UUID tenth = UUID.randomUUID();
        when(contributionRepository.findByUserIdAndActivityDate(USER_ID, LocalDate.parse("2026-09-03")))
                .thenReturn(List.of(contribution(UUID.randomUUID(), "2026-09-03T12:00:00Z"),
                        contribution(tenth, "2026-09-03T02:00:00Z")));

        AnalyticsAchievementsResponse response = read();

        assertThat(badge(response, "FIRST_WORKOUT").earnedOn()).isEqualTo(LocalDate.parse("2026-09-01"));
        Badge ten = badge(response, "WORKOUTS_10");
        assertThat(ten.earnedOn()).isEqualTo(LocalDate.parse("2026-09-03"));
        // The tenth session is the first one of 3 Sept, the one applied earliest.
        assertThat(ten.evidence().sessionId()).isEqualTo(tenth);
        assertThat(badge(response, "WORKOUTS_50").state()).isEqualTo("NEXT");
        assertThat(progress(badge(response, "WORKOUTS_50"))).isEqualTo("11/50 sessions");
    }

    @Test
    void aFullWeekIsAMondayToSundayWeekReachingTheSessionsTarget() {
        sessionsTarget("3");
        trained("2026-09-07", 1);
        trained("2026-09-09", 1);
        // Three sessions by Thursday 10 Sept: the week of 7 Sept is full.
        trained("2026-09-10", 1);
        trained("2026-09-15", 1);

        AnalyticsAchievementsResponse response = read();

        Badge full = badge(response, "FULL_WEEK");
        assertThat(full.earnedOn()).isEqualTo(LocalDate.parse("2026-09-10"));
        assertThat(full.evidence().weekStart()).isEqualTo(LocalDate.parse("2026-09-07"));
        assertThat(full.evidence().weekDays()).containsExactly(true, false, true, true, false, false, false);
        assertThat(badge(response, "FOUR_FULL_WEEKS").state()).isEqualTo("NEXT");
        // The full week was three weeks ago, so no run of full weeks is going.
        assertThat(progress(badge(response, "FOUR_FULL_WEEKS"))).isEqualTo("0/4 weeks");
    }

    @Test
    void fourFullWeeksInARowEarnTheSecondWeekBadgeAndTheRunCountsThisWeekOnceItIsFull() {
        sessionsTarget("2");
        for (String monday : List.of("2026-09-07", "2026-09-14", "2026-09-21", "2026-09-28")) {
            LocalDate day = LocalDate.parse(monday);
            trained(day.toString(), 1);
            trained(day.plusDays(2).toString(), 1);
        }
        trained("2026-10-05", 2);

        AnalyticsAchievementsResponse response = read();

        Badge four = badge(response, "FOUR_FULL_WEEKS");
        assertThat(four.earnedOn()).isEqualTo(LocalDate.parse("2026-09-30"));
        assertThat(four.evidence().weekStart()).isEqualTo(LocalDate.parse("2026-09-28"));
        assertThat(progress(four)).isEqualTo("5/4 weeks");
        assertThat(progress(badge(response, "FULL_WEEK"))).isEqualTo("2/2 sessions");
        // Four full weeks before this one: two sessions each, eight in all, two a week.
        assertThat(response.pace().sessionsPerWeek()).isEqualByComparingTo("2.0");
        assertThat(response.pace().weeks()).isEqualTo(4);
    }

    // ---- getting stronger ----------------------------------------------------------------------------

    @Test
    void recordBadgesUseTheFirstAndTheLastRecordDayInTheUsersZone() {
        AnalyticsUserWorkoutRecordsEntity records = new AnalyticsUserWorkoutRecordsEntity();
        records.setUserId(USER_ID);
        records.setPersonalRecordCount(5);
        // 17:00 UTC is already the next day in Singapore.
        records.setFirstRecordAt(Instant.parse("2026-08-10T17:00:00Z"));
        records.setLastRecordAt(Instant.parse("2026-09-20T03:00:00Z"));
        when(recordsRepository.findById(USER_ID)).thenReturn(Optional.of(records));

        AnalyticsAchievementsResponse response = read();

        assertThat(badge(response, "FIRST_RECORD").earnedOn()).isEqualTo(LocalDate.parse("2026-08-11"));
        assertThat(badge(response, "FIVE_RECORDS").earnedOn()).isEqualTo(LocalDate.parse("2026-09-20"));
        assertThat(progress(badge(response, "FIVE_RECORDS"))).isEqualTo("5/5 records");
        assertThat(badge(response, "BODYWEIGHT_BENCH").state()).isEqualTo("NEXT");
        assertThat(badge(response, "BODYWEIGHT_BENCH").earnedOn()).isNull();
    }

    // ---- eating well ---------------------------------------------------------------------------------

    @Test
    void sevenDaysLoggedCountsAFastDayAndTheRunStillGoingIsTheProgress() {
        for (int day = 1; day <= 6; day++) {
            ate("2026-09-0" + day, 3, "100", null);
        }
        fasted("2026-09-07");
        // A gap on 8 Sept, then the run that is still going: 2 Oct to yesterday, today not logged yet.
        for (String day : List.of("2026-10-02", "2026-10-03", "2026-10-04", "2026-10-05")) {
            ate(day, 2, "90", null);
        }

        AnalyticsAchievementsResponse response = read();

        assertThat(badge(response, "FIRST_MEAL").earnedOn()).isEqualTo(LocalDate.parse("2026-09-01"));
        assertThat(badge(response, "SEVEN_DAYS_LOGGED").earnedOn()).isEqualTo(LocalDate.parse("2026-09-07"));
        assertThat(progress(badge(response, "THIRTY_DAYS_LOGGED"))).isEqualTo("4/30 days");
    }

    @Test
    void aProteinWeekNeedsFiveLoggedDaysAllMetAndEndsOnTheSunday() {
        // Week of 14 Sept: five logged days, one missed, so it does not count.
        for (String day : List.of("2026-09-14", "2026-09-15", "2026-09-16", "2026-09-17")) {
            ate(day, 3, "130", "120");
        }
        ate("2026-09-18", 3, "100", "120");
        // Week of 21 Sept: five logged days, every one met, Sunday and Monday not logged.
        for (String day : List.of("2026-09-22", "2026-09-23", "2026-09-24", "2026-09-25", "2026-09-26")) {
            ate(day, 3, "125", "120");
        }
        // This week: Monday met, today logged but not met yet, which is not a miss.
        ate("2026-10-05", 3, "121", "120");
        ate("2026-10-06", 1, "30", "120");

        AnalyticsAchievementsResponse response = read();

        Badge week = badge(response, "PROTEIN_WEEK");
        assertThat(week.earnedOn()).isEqualTo(LocalDate.parse("2026-09-27"));
        assertThat(week.evidence().weekStart()).isEqualTo(LocalDate.parse("2026-09-21"));
        assertThat(week.evidence().weekDays()).containsExactly(false, true, true, true, true, true, false);
        Badge month = badge(response, "PROTEIN_MONTH");
        assertThat(month.state()).isEqualTo("NEXT");
        assertThat(progress(month)).isEqualTo("1/20 days");
    }

    @Test
    void aProteinMonthNeedsTwentyLoggedDaysAllMetInACalendarMonth() {
        LocalDate day = LocalDate.parse("2026-08-01");
        for (int i = 0; i < 20; i++) {
            ate(day.plusDays(i).toString(), 3, "125", "120");
        }

        AnalyticsAchievementsResponse response = read();

        Badge month = badge(response, "PROTEIN_MONTH");
        assertThat(month.earnedOn()).isEqualTo(LocalDate.parse("2026-08-31"));
        assertThat(month.evidence().monthStart()).isEqualTo(LocalDate.parse("2026-08-01"));
    }

    // ---- in the kitchen ------------------------------------------------------------------------------

    @Test
    void kitchenBadgesCountRecipeCooks() {
        AnalyticsUserCooksEntity cooks = new AnalyticsUserCooksEntity();
        cooks.setUserId(USER_ID);
        cooks.setCooks(10);
        cooks.setFirstCookAt(Instant.parse("2026-08-20T11:00:00Z"));
        cooks.setLastCookAt(Instant.parse("2026-10-01T11:00:00Z"));
        when(cooksRepository.findById(USER_ID)).thenReturn(Optional.of(cooks));

        AnalyticsAchievementsResponse response = read();

        assertThat(badge(response, "FIRST_COOK").earnedOn()).isEqualTo(LocalDate.parse("2026-08-20"));
        assertThat(badge(response, "TEN_COOKS").earnedOn()).isEqualTo(LocalDate.parse("2026-10-01"));
        assertThat(badge(response, "FIFTY_COOKS").state()).isEqualTo("NEXT");
        assertThat(progress(badge(response, "FIFTY_COOKS"))).isEqualTo("10/50 cooks");
        assertThat(response.latest().code()).isEqualTo("TEN_COOKS");
    }

    // ---- earned once ---------------------------------------------------------------------------------

    @Test
    void aBadgeOnceEarnedIsStoredAndStaysEarnedWhenTheDataChanges() {
        trained("2026-09-01", 4);
        trained("2026-09-02", 6);

        AnalyticsAchievementsResponse first = read();
        assertThat(badge(first, "WORKOUTS_10").earnedOn()).isEqualTo(LocalDate.parse("2026-09-02"));
        assertThat(ledgerRows.keySet()).containsExactlyInAnyOrder("FIRST_WORKOUT", "WORKOUTS_10");

        // Sessions are deleted later: the rollups now hold three, and a day later is read again.
        workoutDays.clear();
        trained("2026-09-01", 3);
        AnalyticsAchievementsResponse later = service("2026-10-07T04:00:00Z").getAchievements(principal());

        Badge ten = badge(later, "WORKOUTS_10");
        assertThat(ten.state()).isEqualTo("EARNED");
        assertThat(ten.earnedOn()).isEqualTo(LocalDate.parse("2026-09-02"));
        assertThat(progress(ten)).isEqualTo("3/10 sessions");
        assertThat(later.earned()).isEqualTo(2);
        assertThat(badge(later, "WORKOUTS_50").state()).isEqualTo("NEXT");
    }

    @Test
    void aStoredDateIsNeverRewrittenByALaterComputation() {
        AnalyticsUserAchievementEntity stored = new AnalyticsUserAchievementEntity();
        stored.setUserId(USER_ID);
        stored.setCode("FIRST_WORKOUT");
        stored.setEarnedOn(LocalDate.parse("2026-01-15"));
        stored.setCreated(Instant.parse("2026-01-15T10:00:00Z"));
        ledgerRows.put("FIRST_WORKOUT", stored);
        trained("2026-09-01", 1);

        AnalyticsAchievementsResponse response = read();

        assertThat(badge(response, "FIRST_WORKOUT").earnedOn()).isEqualTo(LocalDate.parse("2026-01-15"));
        assertThat(ledgerRows.get("FIRST_WORKOUT").getEarnedOn()).isEqualTo(LocalDate.parse("2026-01-15"));
    }

    @Test
    void computingTwiceInARowStoresEachBadgeOnceAndKeepsTheFirstDate() {
        trained("2026-09-01", 4);
        trained("2026-09-02", 6);

        AnalyticsAchievementsResponse first = read();
        AnalyticsAchievementsResponse second = read();

        assertThat(badge(second, "WORKOUTS_10").earnedOn()).isEqualTo(badge(first, "WORKOUTS_10").earnedOn());
        verify(achievementRepository, times(2)).saveAndFlush(any(AnalyticsUserAchievementEntity.class));
    }

    @Test
    void aPreExistingRowIsNeitherInsertedAgainNorRewritten() {
        AnalyticsUserAchievementEntity stored = new AnalyticsUserAchievementEntity();
        stored.setUserId(USER_ID);
        stored.setCode("WORKOUTS_10");
        stored.setEarnedOn(LocalDate.parse("2026-08-01"));
        stored.setCreated(Instant.parse("2026-08-01T10:00:00Z"));
        ledgerRows.put("WORKOUTS_10", stored);
        trained("2026-09-02", 10);

        AnalyticsAchievementsResponse response = read();

        assertThat(badge(response, "WORKOUTS_10").earnedOn()).isEqualTo(LocalDate.parse("2026-08-01"));
        verify(achievementRepository, never()).saveAndFlush(stored);
    }

    @Test
    void aBadgeAnotherReadStoredFirstIsShownWithItsStoredDateInsteadOfFailing() {
        trained("2026-09-02", 10);
        // The race: when this read checks, nothing is stored; by its insert, the other read has stored the
        // badge, so the key is taken.
        AnalyticsUserAchievementEntity raced = new AnalyticsUserAchievementEntity();
        raced.setUserId(USER_ID);
        raced.setEarnedOn(LocalDate.parse("2026-09-01"));
        raced.setCreated(Instant.parse("2026-10-06T03:59:59Z"));
        when(achievementRepository.saveAndFlush(any(AnalyticsUserAchievementEntity.class))).thenAnswer(call -> {
            AnalyticsUserAchievementEntity row = call.getArgument(0);
            AnalyticsUserAchievementEntity other = new AnalyticsUserAchievementEntity();
            other.setUserId(USER_ID);
            other.setCode(row.getCode());
            other.setEarnedOn(raced.getEarnedOn());
            other.setCreated(raced.getCreated());
            ledgerRows.put(row.getCode(), other);
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
        });

        AnalyticsAchievementsResponse response = read();

        assertThat(badge(response, "WORKOUTS_10").state()).isEqualTo("EARNED");
        assertThat(badge(response, "WORKOUTS_10").earnedOn()).isEqualTo(LocalDate.parse("2026-09-01"));
        assertThat(response.earned()).isEqualTo(2);
    }

    // ---- retired badges ------------------------------------------------------------------------------

    @Test
    void retiredBadgesShowOnlyUnderEarlierAndOnlyWhenEarned() {
        trainedDays("2026-09-01", 7);

        AnalyticsAchievementsResponse response = read();

        assertThat(response.earlier()).extracting(Badge::code, Badge::earnedOn)
                .containsExactly(tuple("STREAK_7", LocalDate.parse("2026-09-07")));
        assertThat(ledgerRows).containsKey("STREAK_7").doesNotContainKey("STREAK_30");
        assertThat(response.themes().stream().flatMap(theme -> theme.paths().stream())
                .flatMap(path -> path.nodes().stream()).map(Badge::code))
                .doesNotContain("STREAK_7", "STREAK_30", "FIRST_WEIGHT_LOGGED");
        // Retired badges are not counted among the seventeen.
        assertThat(response.earned()).isEqualTo(1);
    }

    @Test
    void anAccountThatNeverEarnedARetiredBadgeHasNoEarlierRow() {
        trainedDays("2026-09-01", 3);

        assertThat(read().earlier()).isEmpty();
    }

    // ---- next up and latest --------------------------------------------------------------------------

    @Test
    void nextUpIsTheTwoClosestNextBadgesOfTheirPaths() {
        // 31 sessions (50 workouts is 62% done) and a logged run of five days (7 days logged is 71% done).
        LocalDate day = LocalDate.parse("2026-08-04");
        for (int i = 0; i < 31; i++) {
            trained(day.plusDays(i * 2L).toString(), 1);
        }
        for (String date : List.of("2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04", "2026-10-05")) {
            ate(date, 2, "90", null);
        }

        AnalyticsAchievementsResponse response = read();

        assertThat(response.nextUp()).extracting(Badge::code, AchievementsServiceTests::progress)
                .containsExactly(
                        tuple("SEVEN_DAYS_LOGGED", "5/7 days"),
                        tuple("WORKOUTS_50", "31/50 sessions"));
        // WORKOUTS_100 is further down the same path, so it is never next up while 50 is not earned.
        assertThat(badge(response, "WORKOUTS_100").state()).isEqualTo("LATER");
        assertThat(response.latest().code()).isEqualTo("FIRST_MEAL");
        assertThat(response.earned()).isEqualTo(3);
    }

    private static AnalyticsWorkoutContributionEntity contribution(UUID sessionId, String appliedAt) {
        AnalyticsWorkoutContributionEntity contribution = new AnalyticsWorkoutContributionEntity();
        contribution.setSessionId(sessionId);
        contribution.setUserId(USER_ID);
        contribution.setLastAppliedAt(Instant.parse(appliedAt));
        return contribution;
    }
}
