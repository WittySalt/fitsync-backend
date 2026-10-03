package com.fitsync.analytics.service;

import java.time.LocalDate;
import java.util.List;

import com.fitsync.analytics.dto.AnalyticsCompareResponse;
import com.fitsync.analytics.dto.ComparePeriod;
import org.junit.jupiter.api.Test;

import static com.fitsync.analytics.service.AnalyticsServiceTestSupport.USER_ID;
import static com.fitsync.analytics.service.AnalyticsServiceTestSupport.nutritionDay;
import static com.fitsync.analytics.service.AnalyticsServiceTestSupport.principal;
import static com.fitsync.analytics.service.AnalyticsServiceTestSupport.workoutDay;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** P-1: compare windows follow the user's zone, and nutrition is compared per logged day. */
class SummaryCompareZoneTests {

    // Sunday 14 June 2026 17:00 UTC is already Monday 15 June 01:00 in Singapore.
    private static final String SUNDAY_EVENING_UTC = "2026-06-14T17:00:00Z";

    @Test
    void windowsUseTheUsersZoneAcrossMidnight() {
        AnalyticsServiceTestSupport t = new AnalyticsServiceTestSupport().withZone("Asia/Singapore");
        when(t.workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(any(), any(), any()))
                .thenReturn(List.of());
        when(t.nutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(any(), any(), any()))
                .thenReturn(List.of());

        AnalyticsCompareResponse singapore = t.service(SUNDAY_EVENING_UTC)
                .getSummaryCompare(principal(), ComparePeriod.WEEK);
        // Monday in Singapore: a new week with only today elapsed.
        assertThat(singapore.currentWindow().from()).isEqualTo(LocalDate.parse("2026-06-15"));
        assertThat(singapore.currentWindow().to()).isEqualTo(LocalDate.parse("2026-06-15"));
        assertThat(singapore.previousWindow().from()).isEqualTo(LocalDate.parse("2026-06-08"));
        assertThat(singapore.previousWindow().to()).isEqualTo(LocalDate.parse("2026-06-08"));
        assertThat(singapore.currentToDate()).isTrue();

        AnalyticsServiceTestSupport utc = new AnalyticsServiceTestSupport().withZone(null);
        when(utc.workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(any(), any(), any()))
                .thenReturn(List.of());
        when(utc.nutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(any(), any(), any()))
                .thenReturn(List.of());
        AnalyticsCompareResponse london = utc.service(SUNDAY_EVENING_UTC)
                .getSummaryCompare(principal(), ComparePeriod.WEEK);
        // Still Sunday in UTC: the week that began on 8 June is complete.
        assertThat(london.currentWindow().from()).isEqualTo(LocalDate.parse("2026-06-08"));
        assertThat(london.currentWindow().to()).isEqualTo(LocalDate.parse("2026-06-14"));
        assertThat(london.currentToDate()).isFalse();
    }

    @Test
    void currentToDateIsTrueMidWeekAndPreviousWindowHasTheSameLength() {
        AnalyticsServiceTestSupport t = new AnalyticsServiceTestSupport().withZone("Asia/Singapore");
        when(t.workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(any(), any(), any()))
                .thenReturn(List.of());
        when(t.nutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(any(), any(), any()))
                .thenReturn(List.of());

        // Saturday 20 June 2026 in Singapore.
        AnalyticsCompareResponse response = t.service("2026-06-20T04:00:00Z")
                .getSummaryCompare(principal(), ComparePeriod.WEEK);

        assertThat(response.currentToDate()).isTrue();
        assertThat(response.currentWindow().from()).isEqualTo(LocalDate.parse("2026-06-15"));
        assertThat(response.currentWindow().to()).isEqualTo(LocalDate.parse("2026-06-20"));
        assertThat(response.previousWindow().from()).isEqualTo(LocalDate.parse("2026-06-08"));
        assertThat(response.previousWindow().to()).isEqualTo(LocalDate.parse("2026-06-13"));
    }

    @Test
    void nutritionIsComparedPerLoggedDayWithDaysLogged() {
        AnalyticsServiceTestSupport t = new AnalyticsServiceTestSupport().withZone("Asia/Singapore");
        // Thursday 18 June 2026: current window 15..18, previous window 8..11.
        when(t.workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                USER_ID, LocalDate.parse("2026-06-15"), LocalDate.parse("2026-06-18")))
                .thenReturn(List.of(workoutDay("2026-06-15", 1, 40), workoutDay("2026-06-17", 2, 70)));
        when(t.workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                USER_ID, LocalDate.parse("2026-06-08"), LocalDate.parse("2026-06-11")))
                .thenReturn(List.of(workoutDay("2026-06-09", 1, 30)));
        when(t.nutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                USER_ID, LocalDate.parse("2026-06-15"), LocalDate.parse("2026-06-18")))
                .thenReturn(List.of(
                        nutritionDay("2026-06-15", 2000, "100.00", 3),
                        nutritionDay("2026-06-16", 2200, "120.00", 3)));
        when(t.nutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                USER_ID, LocalDate.parse("2026-06-08"), LocalDate.parse("2026-06-11")))
                .thenReturn(List.of(
                        nutritionDay("2026-06-08", 1800, "90.00", 3),
                        nutritionDay("2026-06-09", 2000, "100.00", 3),
                        nutritionDay("2026-06-10", 2200, "110.00", 3),
                        nutritionDay("2026-06-11", 0, "0.00", 0)));

        AnalyticsCompareResponse response = t.service("2026-06-18T04:00:00Z")
                .getSummaryCompare(principal(), ComparePeriod.WEEK);

        // Totals are unchanged: this week looks lower only because two days are logged against three.
        assertThat(response.nutrition().totalCaloriesConsumed().current()).isEqualTo(4200.0);
        assertThat(response.nutrition().totalCaloriesConsumed().previous()).isEqualTo(6000.0);
        assertThat(response.nutrition().daysLogged())
                .isEqualTo(new AnalyticsCompareResponse.DaysLogged(2, 3));
        AnalyticsCompareResponse.AveragePerLoggedDay average = response.nutrition().averagePerLoggedDay();
        assertThat(average.calories().current()).isEqualTo(2100.0);
        assertThat(average.calories().previous()).isEqualTo(2000.0);
        assertThat(average.calories().delta()).isEqualTo(100.0);
        assertThat(average.calories().deltaPct()).isEqualTo(5.0);
        assertThat(average.proteinGrams().current()).isEqualTo(110.0);
        assertThat(average.proteinGrams().previous()).isEqualTo(100.0);
        assertThat(response.workout().daysLogged())
                .isEqualTo(new AnalyticsCompareResponse.DaysLogged(2, 1));
    }

    @Test
    void averagesAreNullWhenASideHasNoLoggedDay() {
        AnalyticsServiceTestSupport t = new AnalyticsServiceTestSupport().withZone("Asia/Singapore");
        when(t.workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(any(), any(), any()))
                .thenReturn(List.of());
        when(t.nutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                USER_ID, LocalDate.parse("2026-06-15"), LocalDate.parse("2026-06-18")))
                .thenReturn(List.of(nutritionDay("2026-06-16", 2200, "120.00", 3)));
        when(t.nutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                USER_ID, LocalDate.parse("2026-06-08"), LocalDate.parse("2026-06-11")))
                .thenReturn(List.of());

        AnalyticsCompareResponse response = t.service("2026-06-18T04:00:00Z")
                .getSummaryCompare(principal(), ComparePeriod.WEEK);

        AnalyticsCompareResponse.MetricComparison calories = response.nutrition().averagePerLoggedDay().calories();
        assertThat(calories.current()).isEqualTo(2200.0);
        assertThat(calories.previous()).isNull();
        assertThat(calories.delta()).isNull();
        assertThat(calories.deltaPct()).isNull();
        assertThat(response.nutrition().daysLogged())
                .isEqualTo(new AnalyticsCompareResponse.DaysLogged(1, 0));
    }

    @Test
    void monthWindowEndsAtTheLastDayOfTheMonthForCurrentToDate() {
        AnalyticsServiceTestSupport t = new AnalyticsServiceTestSupport().withZone("Asia/Singapore");
        when(t.workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(any(), any(), any()))
                .thenReturn(List.of());
        when(t.nutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(any(), any(), any()))
                .thenReturn(List.of());

        // 30 June 16:30 UTC is already 1 July in Singapore, the first day of a new month.
        AnalyticsCompareResponse july = t.service("2026-06-30T16:30:00Z")
                .getSummaryCompare(principal(), ComparePeriod.MONTH);
        assertThat(july.currentWindow().from()).isEqualTo(LocalDate.parse("2026-07-01"));
        assertThat(july.currentToDate()).isTrue();

        // On the last day of the month the window is the whole month.
        AnalyticsCompareResponse lastDay = t.service("2026-06-30T10:00:00Z")
                .getSummaryCompare(principal(), ComparePeriod.MONTH);
        assertThat(lastDay.currentWindow().to()).isEqualTo(LocalDate.parse("2026-06-30"));
        assertThat(lastDay.currentToDate()).isFalse();
    }
}
