package com.fitsync.analytics.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.fitsync.analytics.dto.AnalyticsDashboardResponse;
import com.fitsync.analytics.dto.AnalyticsGoalProgressResponse;
import org.junit.jupiter.api.Test;

import static com.fitsync.analytics.service.AnalyticsServiceTestSupport.USER_ID;
import static com.fitsync.analytics.service.AnalyticsServiceTestSupport.goal;
import static com.fitsync.analytics.service.AnalyticsServiceTestSupport.nutritionDay;
import static com.fitsync.analytics.service.AnalyticsServiceTestSupport.principal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

/** H-1: daily nutrition goals read today in the user's zone, never the last row that was written. */
class GoalProgressLiveDayTests {

    // 2026-06-13T17:00Z is Saturday 13 June 17:00 in UTC but already Sunday 14 June 01:00 in Singapore.
    private static final String SG_JUST_AFTER_MIDNIGHT = "2026-06-13T17:00:00Z";

    @Test
    void dailyGoalsReadZeroWhenTodayHasNoRowAfterMidnightInUtcPlus8() {
        AnalyticsServiceTestSupport t = new AnalyticsServiceTestSupport().withZone("Asia/Singapore");
        when(t.goalRepository.findByUserIdOrderByTargetDateAscGoalIdAsc(USER_ID)).thenReturn(List.of(
                goal("CALORIES", "2100", null), goal("PROTEIN", "140", null)));
        // Yesterday in Singapore (13 June) has a row; today (14 June) has none.
        when(t.nutritionRepository.findByUserIdAndActivityDate(USER_ID, LocalDate.parse("2026-06-13")))
                .thenReturn(Optional.of(nutritionDay("2026-06-13", 907, "47.70", 2)));
        when(t.nutritionRepository.findByUserIdAndActivityDate(USER_ID, LocalDate.parse("2026-06-14")))
                .thenReturn(Optional.empty());

        AnalyticsGoalProgressResponse response = t.service(SG_JUST_AFTER_MIDNIGHT)
                .getGoalProgress(principal(), "ACTIVE");

        assertThat(response.goals())
                .extracting(AnalyticsGoalProgressResponse.GoalProgressView::goalType,
                        AnalyticsGoalProgressResponse.GoalProgressView::currentValue,
                        AnalyticsGoalProgressResponse.GoalProgressView::progressRatio,
                        AnalyticsGoalProgressResponse.GoalProgressView::onTrack)
                .containsExactlyInAnyOrder(
                        tuple("CALORIES", 0.0, 0.0, true),
                        tuple("PROTEIN", 0.0, 0.0, false));
    }

    @Test
    void aUtcUserStillSeesSaturdayAtTheSameInstant() {
        AnalyticsServiceTestSupport t = new AnalyticsServiceTestSupport().withZone(null);
        when(t.goalRepository.findByUserIdOrderByTargetDateAscGoalIdAsc(USER_ID))
                .thenReturn(List.of(goal("CALORIES", "2100", null)));
        when(t.nutritionRepository.findByUserIdAndActivityDate(USER_ID, LocalDate.parse("2026-06-13")))
                .thenReturn(Optional.of(nutritionDay("2026-06-13", 907, "47.70", 2)));

        AnalyticsGoalProgressResponse response = t.service(SG_JUST_AFTER_MIDNIGHT)
                .getGoalProgress(principal(), "ACTIVE");

        // For a UTC user it is still Saturday, so Saturday's total is today's total.
        assertThat(response.goals().get(0).currentValue()).isEqualTo(907.0);
        assertThat(response.goals().get(0).progressRatio()).isEqualTo(0.4319);
    }

    @Test
    void dailyGoalsUseTodaysRowAndKeepCeilingAndFloorRules() {
        AnalyticsServiceTestSupport t = new AnalyticsServiceTestSupport().withZone("Asia/Singapore");
        when(t.goalRepository.findByUserIdOrderByTargetDateAscGoalIdAsc(USER_ID)).thenReturn(List.of(
                goal("CALORIES", "1500", null), goal("PROTEIN", "100", null), goal("CARBS", "90", null)));
        when(t.nutritionRepository.findByUserIdAndActivityDate(USER_ID, LocalDate.parse("2026-06-14")))
                .thenReturn(Optional.of(nutritionDay("2026-06-14", 1800, "120.00", 3)));

        AnalyticsGoalProgressResponse response = t.service(SG_JUST_AFTER_MIDNIGHT)
                .getGoalProgress(principal(), "ACTIVE");

        assertThat(response.goals())
                .extracting(AnalyticsGoalProgressResponse.GoalProgressView::goalType,
                        AnalyticsGoalProgressResponse.GoalProgressView::currentValue,
                        AnalyticsGoalProgressResponse.GoalProgressView::progressRatio,
                        AnalyticsGoalProgressResponse.GoalProgressView::onTrack)
                .containsExactlyInAnyOrder(
                        tuple("CALORIES", 1800.0, 1.2, false),
                        tuple("PROTEIN", 120.0, 1.2, true),
                        tuple("CARBS", 100.0, 1.1111, false));
    }

    @Test
    void expiryAndDaysRemainingFollowTheUsersZone() {
        // The target date is Saturday 13 June. In Singapore it is already Sunday 14 June, so it is expired.
        AnalyticsServiceTestSupport sg = new AnalyticsServiceTestSupport().withZone("Asia/Singapore");
        when(sg.goalRepository.findByUserIdOrderByTargetDateAscGoalIdAsc(USER_ID))
                .thenReturn(List.of(goal("WEIGHT", "70", LocalDate.parse("2026-06-13"))));
        AnalyticsGoalProgressResponse.GoalProgressView singapore = sg.service(SG_JUST_AFTER_MIDNIGHT)
                .getGoalProgress(principal(), "ACTIVE").goals().get(0);
        assertThat(singapore.expired()).isTrue();
        assertThat(singapore.onTrack()).isFalse();
        assertThat(singapore.daysRemaining()).isZero();

        AnalyticsServiceTestSupport utc = new AnalyticsServiceTestSupport().withZone("UTC");
        when(utc.goalRepository.findByUserIdOrderByTargetDateAscGoalIdAsc(USER_ID))
                .thenReturn(List.of(goal("WEIGHT", "70", LocalDate.parse("2026-06-13"))));
        AnalyticsGoalProgressResponse.GoalProgressView inUtc = utc.service(SG_JUST_AFTER_MIDNIGHT)
                .getGoalProgress(principal(), "ACTIVE").goals().get(0);
        assertThat(inUtc.expired()).isFalse();
        assertThat(inUtc.onTrack()).isTrue();
        assertThat(inUtc.daysRemaining()).isZero();
    }

    @Test
    void dashboardGoalsAreAlsoEvaluatedOnToday() {
        AnalyticsServiceTestSupport t = new AnalyticsServiceTestSupport().withZone("Asia/Singapore");
        when(t.goalRepository.findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(USER_ID, "ACTIVE"))
                .thenReturn(List.of(goal("CALORIES", "2100", null)));

        AnalyticsDashboardResponse response = t.service(SG_JUST_AFTER_MIDNIGHT).getDashboard(principal());

        assertThat(response.goals()).hasSize(1);
        assertThat(response.goals().get(0).currentValue()).isZero();
        assertThat(response.goals().get(0).progressRatio()).isZero();
    }
}
