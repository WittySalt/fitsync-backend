package com.fitsync.analytics.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.analytics.config.AnalyticsProperties;
import com.fitsync.analytics.dto.AnalyticsDashboardResponse;
import com.fitsync.analytics.dto.AnalyticsStreaksResponse;
import com.fitsync.analytics.entity.AnalyticsUserDailyBodyEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyWorkoutEntity;
import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import com.fitsync.analytics.entity.AnalyticsUserGoalProgressEntity;
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import com.fitsync.analytics.repository.AnalyticsUserStreakRollupRepository;
import com.fitsync.analytics.repository.AnalyticsUserWorkoutRecordsRepository;
import com.fitsync.analytics.security.CurrentUserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the stale-read-model defects found on 2026-09-26.
 *
 * <p>The dashboard snapshot is only rewritten when an event arrives. A user who stops logging keeps
 * whatever it last held, and the read path trusted it verbatim. Against the demo account, whose last
 * activity was 5 July, the live system reported on 26 September: a current streak of four days, one
 * workout "today" and five "this week". The client rendered that beside a month view reading zero
 * workouts, so the dashboard contradicted itself on one screen.
 *
 * <p>Goal expiry had the same shape. It was applied when goal events were consumed and by the
 * nightly rollup, but nothing re-evaluated a goal whose target date passed in between, so three
 * goals dated 31 August were still reported as ACTIVE and on track four weeks later.
 *
 * <p>The common fault: a time-dependent predicate applied only at write time. Every test here fixes
 * the clock well past the stored data, which is the condition none of the previous tests covered.
 */
class AnalyticsStaleReadModelTests {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final CurrentUserPrincipal PRINCIPAL =
            new CurrentUserPrincipal(USER_ID, "demo@fitsync.app", "Demo", "USER");

    /** Matches the live reproduction: data from 5 July, read on 26 September. */
    private static final Instant SNAPSHOT_AT = Instant.parse("2026-07-04T19:13:37Z");
    private static final LocalDate LAST_ACTIVE = LocalDate.parse("2026-07-05");
    private static final Instant TODAY = Instant.parse("2026-09-26T08:00:00Z");

    private final AnalyticsUserDashboardSnapshotRepository dashboardRepository =
            mock(AnalyticsUserDashboardSnapshotRepository.class);
    private final AnalyticsUserGoalProgressRepository goalRepository =
            mock(AnalyticsUserGoalProgressRepository.class);
    private final AnalyticsUserDailyBodyRepository bodyRepository =
            mock(AnalyticsUserDailyBodyRepository.class);
    private final AnalyticsUserDailyWorkoutRepository workoutRepository =
            mock(AnalyticsUserDailyWorkoutRepository.class);
    private final AnalyticsUserStreakRollupRepository streakRollupRepository =
            mock(AnalyticsUserStreakRollupRepository.class);

    @Test
    @DisplayName("a streak whose last active day has passed is no longer current")
    void brokenStreakIsNotReportedAsCurrent() {
        givenSnapshotWithStreak(4);
        givenLastActiveDay(LAST_ACTIVE);

        AnalyticsStreaksResponse response = serviceAt(TODAY).getStreaks(PRINCIPAL);

        assertThat(response.currentStreakDays())
                .withFailMessage("a streak last extended on %s cannot still be running on 26 Sept",
                        LAST_ACTIVE)
                .isZero();
        assertThat(response.lastActiveDate()).isEqualTo(LAST_ACTIVE);
    }

    @Test
    @DisplayName("a streak extended yesterday is still current")
    void yesterdaysStreakSurvives() {
        givenSnapshotWithStreak(4);
        givenLastActiveDay(LocalDate.parse("2026-09-25"));

        assertThat(serviceAt(TODAY).getStreaks(PRINCIPAL).currentStreakDays()).isEqualTo(4);
    }

    @Test
    @DisplayName("a streak extended today is still current")
    void todaysStreakSurvives() {
        givenSnapshotWithStreak(4);
        givenLastActiveDay(LocalDate.parse("2026-09-26"));

        assertThat(serviceAt(TODAY).getStreaks(PRINCIPAL).currentStreakDays()).isEqualTo(4);
    }

    @Test
    @DisplayName("a stale snapshot does not report its counters as today's or this week's")
    void staleSnapshotCountersAreZeroed() {
        AnalyticsUserDashboardSnapshotEntity snapshot = givenSnapshotWithStreak(4);
        snapshot.setTodayWorkoutCount(1);
        snapshot.setWeekWorkoutCount(5);
        snapshot.setWeekWorkoutMinutes(178);
        snapshot.setTodayCaloriesConsumed(1860);
        snapshot.setTodayCaloriesTarget(2100);
        snapshot.setTodayProteinGrams(new BigDecimal("132.50"));
        givenLastActiveDay(LAST_ACTIVE);

        AnalyticsDashboardResponse response = serviceAt(TODAY).getDashboard(PRINCIPAL);

        assertThat(response.workout().todayWorkoutCount()).isZero();
        assertThat(response.workout().weekWorkoutCount()).isZero();
        assertThat(response.workout().weekWorkoutMinutes()).isZero();
        assertThat(response.workout().activeStreakDays())
                .withFailMessage("the dashboard and the streaks endpoint must agree")
                .isZero();
        assertThat(response.nutrition().todayCaloriesConsumed()).isZero();
        assertThat(response.nutrition().todayProteinGrams()).isZero();

        assertThat(response.nutrition().todayCaloriesTarget())
                .withFailMessage("a target is not activity and does not go stale")
                .isEqualTo(2100);
        assertThat(response.body().latestWeightKg())
                .withFailMessage("the newest body reading is still the newest reading")
                .isEqualTo(78.4);
    }

    @Test
    @DisplayName("a same-day snapshot is passed through untouched")
    void freshSnapshotIsNotZeroed() {
        AnalyticsUserDashboardSnapshotEntity snapshot = givenSnapshotWithStreak(4);
        snapshot.setGeneratedAt(Instant.parse("2026-09-26T02:00:00Z"));
        snapshot.setTodayWorkoutCount(1);
        snapshot.setWeekWorkoutCount(5);
        snapshot.setWeekWorkoutMinutes(178);
        snapshot.setTodayCaloriesConsumed(1860);
        givenLastActiveDay(LocalDate.parse("2026-09-26"));

        AnalyticsDashboardResponse response = serviceAt(TODAY).getDashboard(PRINCIPAL);

        assertThat(response.workout().todayWorkoutCount()).isEqualTo(1);
        assertThat(response.workout().weekWorkoutCount()).isEqualTo(5);
        assertThat(response.workout().weekWorkoutMinutes()).isEqualTo(178);
        assertThat(response.workout().activeStreakDays()).isEqualTo(4);
        assertThat(response.nutrition().todayCaloriesConsumed()).isEqualTo(1860);
    }

    @Test
    @DisplayName("the dashboard carries the real measurement date, not the snapshot's")
    void bodyMetricCarriesItsOwnDate() {
        givenSnapshotWithStreak(0);
        givenLastActiveDay(LAST_ACTIVE);
        AnalyticsUserDailyBodyEntity body = new AnalyticsUserDailyBodyEntity();
        body.setUserId(USER_ID);
        body.setMetricDate(LocalDate.parse("2026-07-03"));
        when(bodyRepository.findTopByUserIdOrderByMetricDateDesc(USER_ID)).thenReturn(Optional.of(body));

        AnalyticsDashboardResponse response = serviceAt(TODAY).getDashboard(PRINCIPAL);

        assertThat(response.body().measuredOn())
                .withFailMessage("without this the client captions the card with generatedAt, "
                        + "which is when analytics last ran, not when the user was weighed")
                .isEqualTo(LocalDate.parse("2026-07-03"));
        assertThat(response.body().measuredOn()).isNotEqualTo(
                SNAPSHOT_AT.atZone(ZoneOffset.UTC).toLocalDate());
    }

    @Test
    @DisplayName("a goal past its target date is expired and never on track")
    void expiredGoalIsNotOnTrack() {
        givenSnapshotWithStreak(0);
        givenLastActiveDay(LAST_ACTIVE);
        AnalyticsUserGoalProgressEntity goal = new AnalyticsUserGoalProgressEntity();
        goal.setUserId(USER_ID);
        goal.setGoalId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        goal.setGoalType("PROTEIN");
        goal.setGoalStatus("ACTIVE");
        goal.setTargetValue(new BigDecimal("140.00"));
        goal.setTargetUnit("g");
        goal.setTargetDate(LocalDate.parse("2026-08-31"));
        goal.setOnTrack(true);
        when(goalRepository.findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(USER_ID, "ACTIVE"))
                .thenReturn(List.of(goal));

        AnalyticsDashboardResponse.GoalProgressSummary summary =
                serviceAt(TODAY).getDashboard(PRINCIPAL).goals().get(0);

        assertThat(summary.expired()).isTrue();
        assertThat(summary.onTrack())
                .withFailMessage("a goal whose deadline passed on 31 Aug cannot be on track on 26 Sept")
                .isFalse();
    }

    @Test
    @DisplayName("a goal still inside its target date keeps its stored on-track flag")
    void liveGoalKeepsOnTrack() {
        givenSnapshotWithStreak(0);
        givenLastActiveDay(LAST_ACTIVE);
        AnalyticsUserGoalProgressEntity goal = new AnalyticsUserGoalProgressEntity();
        goal.setUserId(USER_ID);
        goal.setGoalId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        goal.setGoalType("WEIGHT");
        goal.setGoalStatus("ACTIVE");
        goal.setTargetDate(LocalDate.parse("2026-12-31"));
        goal.setOnTrack(true);
        when(goalRepository.findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(USER_ID, "ACTIVE"))
                .thenReturn(List.of(goal));

        AnalyticsDashboardResponse.GoalProgressSummary summary =
                serviceAt(TODAY).getDashboard(PRINCIPAL).goals().get(0);

        assertThat(summary.expired()).isFalse();
        assertThat(summary.onTrack()).isTrue();
    }

    // ---------------------------------------------------------------------------------------

    private AnalyticsUserDashboardSnapshotEntity givenSnapshotWithStreak(int streakDays) {
        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setUserId(USER_ID);
        snapshot.setTimezone("Asia/Singapore");
        snapshot.setGeneratedAt(SNAPSHOT_AT);
        snapshot.setLatestWeightKg(new BigDecimal("78.40"));
        snapshot.setActiveStreakDays(streakDays);
        when(dashboardRepository.findById(USER_ID)).thenReturn(Optional.of(snapshot));
        when(goalRepository.findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(USER_ID, "ACTIVE"))
                .thenReturn(List.of());
        return snapshot;
    }

    private void givenLastActiveDay(LocalDate day) {
        AnalyticsUserDailyWorkoutEntity row = new AnalyticsUserDailyWorkoutEntity();
        row.setUserId(USER_ID);
        row.setActivityDate(day);
        when(workoutRepository.findTopByUserIdAndCompletedWorkoutCountGreaterThanOrderByActivityDateDesc(
                any(UUID.class), anyInt())).thenReturn(Optional.of(row));
    }

    private AnalyticsServiceImpl serviceAt(Instant now) {
        return new AnalyticsServiceImpl(
                dashboardRepository,
                goalRepository,
                bodyRepository,
                workoutRepository,
                mock(AnalyticsUserDailyNutritionRepository.class),
                streakRollupRepository,
                mock(AnalyticsUserWorkoutRecordsRepository.class),
                Clock.fixed(now, ZoneOffset.UTC),
                new AnalyticsProperties(new AnalyticsProperties.Trends(
                        new AnalyticsProperties.MaxRangeDays(370, 1095, 1830))));
    }
}
