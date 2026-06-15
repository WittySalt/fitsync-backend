package com.fitsync.analytics.service;

import java.time.LocalDate;

import com.fitsync.analytics.dto.AnalyticsBodyTrendsResponse;
import com.fitsync.analytics.dto.AnalyticsDashboardResponse;
import com.fitsync.analytics.dto.AnalyticsGoalProgressResponse;
import com.fitsync.analytics.dto.AnalyticsNutritionSummaryResponse;
import com.fitsync.analytics.dto.AnalyticsWorkoutSummaryResponse;
import com.fitsync.analytics.dto.BodyTrendBucket;
import com.fitsync.analytics.security.CurrentUserPrincipal;

public interface AnalyticsService {

    AnalyticsDashboardResponse getDashboard(CurrentUserPrincipal principal);

    AnalyticsBodyTrendsResponse getBodyTrends(CurrentUserPrincipal principal, LocalDate from, LocalDate to, BodyTrendBucket bucket);

    AnalyticsWorkoutSummaryResponse getWorkoutSummary(CurrentUserPrincipal principal, LocalDate from, LocalDate to);

    AnalyticsNutritionSummaryResponse getNutritionSummary(CurrentUserPrincipal principal, LocalDate from, LocalDate to);

    AnalyticsGoalProgressResponse getGoalProgress(CurrentUserPrincipal principal, String status);
}
