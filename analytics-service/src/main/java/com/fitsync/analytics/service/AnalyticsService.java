package com.fitsync.analytics.service;

import java.time.LocalDate;

import com.fitsync.analytics.dto.AnalyticsActivityCalendarResponse;
import com.fitsync.analytics.dto.AnalyticsBodyTrendsResponse;
import com.fitsync.analytics.dto.AnalyticsCompareResponse;
import com.fitsync.analytics.dto.AnalyticsDashboardResponse;
import com.fitsync.analytics.dto.AnalyticsGoalProgressResponse;
import com.fitsync.analytics.dto.AnalyticsNutritionSeriesResponse;
import com.fitsync.analytics.dto.AnalyticsNutritionSummaryResponse;
import com.fitsync.analytics.dto.AnalyticsStreaksResponse;
import com.fitsync.analytics.dto.AnalyticsWeeklyReportResponse;
import com.fitsync.analytics.dto.AnalyticsWorkoutSeriesResponse;
import com.fitsync.analytics.dto.AnalyticsWorkoutSummaryResponse;
import com.fitsync.analytics.dto.BodyTrendBucket;
import com.fitsync.analytics.dto.ComparePeriod;
import com.fitsync.analytics.security.CurrentUserPrincipal;

public interface AnalyticsService {

    AnalyticsDashboardResponse getDashboard(CurrentUserPrincipal principal);

    AnalyticsBodyTrendsResponse getBodyTrends(CurrentUserPrincipal principal, LocalDate from, LocalDate to, BodyTrendBucket bucket);

    AnalyticsWorkoutSummaryResponse getWorkoutSummary(CurrentUserPrincipal principal, LocalDate from, LocalDate to);

    AnalyticsWorkoutSeriesResponse getWorkoutSeries(CurrentUserPrincipal principal, LocalDate from, LocalDate to, BodyTrendBucket bucket);

    AnalyticsNutritionSummaryResponse getNutritionSummary(CurrentUserPrincipal principal, LocalDate from, LocalDate to);

    AnalyticsNutritionSeriesResponse getNutritionSeries(CurrentUserPrincipal principal, LocalDate from, LocalDate to, BodyTrendBucket bucket);

    AnalyticsCompareResponse getSummaryCompare(CurrentUserPrincipal principal, ComparePeriod period);

    AnalyticsActivityCalendarResponse getActivityCalendar(CurrentUserPrincipal principal, LocalDate from, LocalDate to);

    AnalyticsGoalProgressResponse getGoalProgress(CurrentUserPrincipal principal, String status);

    AnalyticsStreaksResponse getStreaks(CurrentUserPrincipal principal);

    AnalyticsWeeklyReportResponse getWeeklyReport(CurrentUserPrincipal principal, LocalDate weekStart);
}
