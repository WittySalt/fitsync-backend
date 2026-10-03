package com.fitsync.analytics.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.fitsync.analytics.dto.AnalyticsAchievementsResponse;
import com.fitsync.analytics.dto.AnalyticsDashboardResponse;
import org.junit.jupiter.api.Test;

import static com.fitsync.analytics.service.AnalyticsServiceTestSupport.USER_ID;
import static com.fitsync.analytics.service.AnalyticsServiceTestSupport.principal;
import static com.fitsync.analytics.service.AnalyticsServiceTestSupport.workoutDay;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** H-2: one definition of "this week" (Monday to Sunday, user zone) and of a streak (workout days). */
class WeekAndStreakDefinitionTests {

    @Test
    void dashboardCountsMondayToSundayInTheUsersZone() {
        AnalyticsServiceTestSupport t = new AnalyticsServiceTestSupport().withZone("Asia/Singapore");
        t.snapshot.setActiveStreakDays(2);
        t.snapshot.setGeneratedAt(java.time.Instant.parse("2026-06-14T17:00:00Z"));
        t.snapshot.setWeekWorkoutCount(3);
        t.snapshot.setWeekWorkoutMinutes(145);
        // 2026-06-14T17:30Z is Monday 15 June 01:30 in Singapore (still Sunday 14 June in UTC).
        // The new week is 15..21 June; last week's three workouts must not be counted.
        when(t.workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                USER_ID, LocalDate.parse("2026-06-15"), LocalDate.parse("2026-06-21")))
                .thenReturn(List.of());
        when(t.workoutRepository.findTopByUserIdAndCompletedWorkoutCountGreaterThanOrderByActivityDateDesc(USER_ID, 0))
                .thenReturn(Optional.of(workoutDay("2026-06-14", 1, 30)));

        AnalyticsDashboardResponse.WorkoutSummary workout =
                t.service("2026-06-14T17:30:00Z").getDashboard(principal()).workout();

        assertThat(workout.workoutsThisWeek()).isZero();
        assertThat(workout.minutesThisWeek()).isZero();
        // Last workout was yesterday, so the streak is still alive.
        assertThat(workout.currentStreakDays()).isEqualTo(2);
        assertThat(workout.activeStreakDays()).isEqualTo(2);
    }

    @Test
    void dashboardSumsTheWholeCalendarWeekAndKeepsTheOldFields() {
        AnalyticsServiceTestSupport t = new AnalyticsServiceTestSupport().withZone("Asia/Singapore");
        t.snapshot.setActiveStreakDays(1);
        // Saturday 20 June 2026 in Singapore.
        t.snapshot.setGeneratedAt(java.time.Instant.parse("2026-06-20T03:00:00Z"));
        t.snapshot.setTodayWorkoutCount(1);
        t.snapshot.setWeekWorkoutCount(3);
        t.snapshot.setWeekWorkoutMinutes(145);
        when(t.workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                USER_ID, LocalDate.parse("2026-06-15"), LocalDate.parse("2026-06-21")))
                .thenReturn(List.of(workoutDay("2026-06-16", 1, 50), workoutDay("2026-06-20", 1, 40)));
        when(t.workoutRepository.findTopByUserIdAndCompletedWorkoutCountGreaterThanOrderByActivityDateDesc(USER_ID, 0))
                .thenReturn(Optional.of(workoutDay("2026-06-20", 1, 40)));

        AnalyticsDashboardResponse.WorkoutSummary workout =
                t.service("2026-06-20T04:00:00Z").getDashboard(principal()).workout();

        assertThat(workout.workoutsThisWeek()).isEqualTo(2);
        assertThat(workout.minutesThisWeek()).isEqualTo(90);
        assertThat(workout.weekWorkoutCount()).isEqualTo(3);
        assertThat(workout.weekWorkoutMinutes()).isEqualTo(145);
        assertThat(workout.todayWorkoutCount()).isEqualTo(1);
        assertThat(workout.currentStreakDays()).isEqualTo(1);
    }

    @Test
    void streakIsZeroWhenTheLastWorkoutWasBeforeYesterday() {
        AnalyticsServiceTestSupport t = new AnalyticsServiceTestSupport().withZone("Asia/Singapore");
        t.snapshot.setActiveStreakDays(4);
        t.snapshot.setGeneratedAt(java.time.Instant.parse("2026-06-20T03:00:00Z"));
        when(t.workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(any(), any(), any()))
                .thenReturn(List.of());
        when(t.workoutRepository.findTopByUserIdAndCompletedWorkoutCountGreaterThanOrderByActivityDateDesc(USER_ID, 0))
                .thenReturn(Optional.of(workoutDay("2026-06-17", 1, 40)));

        AnalyticsDashboardResponse.WorkoutSummary workout =
                t.service("2026-06-20T04:00:00Z").getDashboard(principal()).workout();

        assertThat(workout.currentStreakDays()).isZero();
    }

    @Test
    void streakAchievementsAreRenamedAndCountWorkoutDaysInARow() {
        AnalyticsServiceTestSupport t = new AnalyticsServiceTestSupport().withZone("Asia/Singapore");
        t.snapshot.setActiveStreakDays(7);
        when(t.workoutRepository.findByUserIdOrderByActivityDateAsc(USER_ID)).thenReturn(List.of(
                workoutDay("2026-06-14", 1, 30), workoutDay("2026-06-15", 1, 30),
                workoutDay("2026-06-16", 1, 30), workoutDay("2026-06-17", 1, 30),
                workoutDay("2026-06-18", 1, 30), workoutDay("2026-06-19", 1, 30),
                workoutDay("2026-06-20", 1, 30)));
        when(t.streakRollupRepository.findById(USER_ID)).thenReturn(Optional.empty());

        AnalyticsAchievementsResponse response = t.service("2026-06-20T04:00:00Z").getAchievements(principal());

        assertThat(response.achievements())
                .filteredOn(view -> view.code().startsWith("STREAK_"))
                .extracting(AnalyticsAchievementsResponse.AchievementView::title,
                        AnalyticsAchievementsResponse.AchievementView::unlocked)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("7 days in a row", true),
                        org.assertj.core.groups.Tuple.tuple("30 days in a row", false));
    }

    @Test
    void streakAchievementDoesNotCountABrokenStreakAsCurrent() {
        AnalyticsServiceTestSupport t = new AnalyticsServiceTestSupport().withZone("Asia/Singapore");
        // A stored counter of 9 from a streak that ended on 3 June is not a live streak on 20 June.
        t.snapshot.setActiveStreakDays(9);
        when(t.workoutRepository.findByUserIdOrderByActivityDateAsc(USER_ID))
                .thenReturn(List.of(workoutDay("2026-06-03", 1, 30)));
        when(t.streakRollupRepository.findById(USER_ID)).thenReturn(Optional.empty());

        AnalyticsAchievementsResponse response = t.service("2026-06-20T04:00:00Z").getAchievements(principal());

        assertThat(response.achievements())
                .filteredOn(view -> "STREAK_7".equals(view.code()))
                .allSatisfy(view -> {
                    assertThat(view.unlocked()).isFalse();
                    assertThat(view.progress()).isZero();
                });
    }
}
