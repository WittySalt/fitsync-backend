package com.fitsync.analytics.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import java.time.LocalDate;

import com.fitsync.analytics.dto.AnalyticsActivityCalendarResponse;
import com.fitsync.analytics.dto.AnalyticsBodyTrendsResponse;
import com.fitsync.analytics.dto.AnalyticsCompareResponse;
import com.fitsync.analytics.dto.AnalyticsDashboardResponse;
import com.fitsync.analytics.dto.AnalyticsGoalProgressResponse;
import com.fitsync.analytics.dto.AnalyticsNutritionSeriesResponse;
import com.fitsync.analytics.dto.AnalyticsNutritionSummaryResponse;
import com.fitsync.analytics.dto.AnalyticsWorkoutSeriesResponse;
import com.fitsync.analytics.dto.AnalyticsWorkoutSummaryResponse;
import com.fitsync.analytics.security.CurrentUserPrincipal;
import com.fitsync.analytics.security.JwtService;
import com.fitsync.analytics.service.AnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AnalyticsController.class)
@Import(com.fitsync.web.config.CommonWebErrorConfiguration.class)
@ActiveProfiles("test")
class AnalyticsControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService analyticsService;

    @MockBean
    private JwtService jwtService;

    @Test
    void shouldRequireAuthenticationForDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnDashboardForAuthenticatedUser() throws Exception {
        UUID goalId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        CurrentUserPrincipal principal = principal();
        AnalyticsDashboardResponse response = new AnalyticsDashboardResponse(
                Instant.parse("2026-06-14T08:00:00Z"),
                "Asia/Singapore",
                new AnalyticsDashboardResponse.BodySummary(78.4, 19.8, 24.7, -1.6),
                new AnalyticsDashboardResponse.WorkoutSummary(1, 4, 205, 3),
                new AnalyticsDashboardResponse.NutritionSummary(1860, 2100, 132.5, 150.0),
                List.of(new AnalyticsDashboardResponse.GoalProgressSummary(
                        goalId, "WEIGHT", 75.0, "kg", LocalDate.parse("2026-08-01"), 77.0, 0.42, true,
                        Instant.parse("2026-06-14T07:00:00Z"))));

        when(analyticsService.getDashboard(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/analytics/dashboard")
                        .with(authentication(authToken(principal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Asia/Singapore"))
                .andExpect(jsonPath("$.workout.weekWorkoutCount").value(4))
                .andExpect(jsonPath("$.goals[0].goalId").value(goalId.toString()));
    }

    @Test
    void shouldReturnWorkoutSummaryForAuthenticatedUser() throws Exception {
        CurrentUserPrincipal principal = principal();
        when(analyticsService.getWorkoutSummary(any(), any(), any())).thenReturn(
                new AnalyticsWorkoutSummaryResponse(LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-14"),
                        5, 210, 1500, 20, 42.0, 3));

        mockMvc.perform(get("/api/v1/analytics/workouts/summary")
                        .with(authentication(authToken(principal)))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalWorkoutCount").value(5))
                .andExpect(jsonPath("$.activeStreakDays").value(3));
    }

    @Test
    void shouldReturnWorkoutSeriesForAuthenticatedUser() throws Exception {
        CurrentUserPrincipal principal = principal();
        when(analyticsService.getWorkoutSeries(any(), any(), any(), any())).thenReturn(
                new AnalyticsWorkoutSeriesResponse(
                        LocalDate.parse("2026-06-01"),
                        LocalDate.parse("2026-06-14"),
                        "WEEK",
                        List.of(new AnalyticsWorkoutSeriesResponse.WorkoutSeriesPoint(
                                LocalDate.parse("2026-06-01"), 3, 120, 900, 14))));

        mockMvc.perform(get("/api/v1/analytics/workouts/series")
                        .with(authentication(authToken(principal)))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-14")
                        .param("bucket", "WEEK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucket").value("WEEK"))
                .andExpect(jsonPath("$.points[0].date").value("2026-06-01"))
                .andExpect(jsonPath("$.points[0].completedWorkoutCount").value(3))
                .andExpect(jsonPath("$.points[0].totalWorkoutMinutes").value(120));
    }

    @Test
    void shouldReturnActivityCalendarForAuthenticatedUser() throws Exception {
        CurrentUserPrincipal principal = principal();
        when(analyticsService.getActivityCalendar(any(), any(), any())).thenReturn(
                new AnalyticsActivityCalendarResponse(
                        LocalDate.parse("2026-06-01"),
                        LocalDate.parse("2026-06-03"),
                        List.of(
                                new AnalyticsActivityCalendarResponse.ActivityCalendarDay(LocalDate.parse("2026-06-01"), 2, 60),
                                new AnalyticsActivityCalendarResponse.ActivityCalendarDay(LocalDate.parse("2026-06-02"), 0, 0),
                                new AnalyticsActivityCalendarResponse.ActivityCalendarDay(LocalDate.parse("2026-06-03"), 1, 30))));

        mockMvc.perform(get("/api/v1/analytics/activity/calendar")
                        .with(authentication(authToken(principal)))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].date").value("2026-06-01"))
                .andExpect(jsonPath("$.days[0].workoutCount").value(2))
                .andExpect(jsonPath("$.days[1].workoutCount").value(0))
                .andExpect(jsonPath("$.days[2].workoutMinutes").value(30));
    }

    @Test
    void shouldReturnNutritionSummaryForAuthenticatedUser() throws Exception {
        CurrentUserPrincipal principal = principal();
        when(analyticsService.getNutritionSummary(any(), any(), any())).thenReturn(
                new AnalyticsNutritionSummaryResponse(LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-14"),
                        5400, 420.0, 500.0, 160.0, 6000, 450.0, 0.9, 0.93, 7));

        mockMvc.perform(get("/api/v1/analytics/nutrition/summary")
                        .with(authentication(authToken(principal)))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCaloriesConsumed").value(5400))
                .andExpect(jsonPath("$.daysWithinCalorieTargetBand").value(7));
    }

    @Test
    void shouldReturnNutritionSeriesForAuthenticatedUser() throws Exception {
        CurrentUserPrincipal principal = principal();
        when(analyticsService.getNutritionSeries(any(), any(), any(), any())).thenReturn(
                new AnalyticsNutritionSeriesResponse(
                        LocalDate.parse("2026-06-01"),
                        LocalDate.parse("2026-06-14"),
                        "MONTH",
                        List.of(new AnalyticsNutritionSeriesResponse.NutritionSeriesPoint(
                                LocalDate.parse("2026-06-01"), 3800, 270.0, 420.0, 130.0, 4300, 310.0))));

        mockMvc.perform(get("/api/v1/analytics/nutrition/series")
                        .with(authentication(authToken(principal)))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-14")
                        .param("bucket", "MONTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucket").value("MONTH"))
                .andExpect(jsonPath("$.points[0].date").value("2026-06-01"))
                .andExpect(jsonPath("$.points[0].totalCaloriesConsumed").value(3800))
                .andExpect(jsonPath("$.points[0].totalProteinGrams").value(270.0));
    }

    @Test
    void shouldReturnSummaryCompareForAuthenticatedUser() throws Exception {
        CurrentUserPrincipal principal = principal();
        when(analyticsService.getSummaryCompare(any(), any())).thenReturn(new AnalyticsCompareResponse(
                "WEEK",
                new AnalyticsCompareResponse.PeriodWindow(
                        LocalDate.parse("2026-06-15"), LocalDate.parse("2026-06-18")),
                new AnalyticsCompareResponse.PeriodWindow(
                        LocalDate.parse("2026-06-08"), LocalDate.parse("2026-06-11")),
                new AnalyticsCompareResponse.WorkoutComparison(
                        new AnalyticsCompareResponse.MetricComparison(3.0, 2.0, 1.0, 50.0),
                        new AnalyticsCompareResponse.MetricComparison(90.0, 45.0, 45.0, 100.0),
                        new AnalyticsCompareResponse.MetricComparison(750.0, 300.0, 450.0, 150.0),
                        new AnalyticsCompareResponse.MetricComparison(12.0, 6.0, 6.0, 100.0)),
                new AnalyticsCompareResponse.NutritionComparison(
                        new AnalyticsCompareResponse.MetricComparison(4000.0, 3000.0, 1000.0, 33.3333),
                        new AnalyticsCompareResponse.MetricComparison(260.0, 200.0, 60.0, 30.0),
                        new AnalyticsCompareResponse.MetricComparison(420.0, 300.0, 120.0, 40.0),
                        new AnalyticsCompareResponse.MetricComparison(130.0, 100.0, 30.0, 30.0))));

        mockMvc.perform(get("/api/v1/analytics/summary/compare")
                        .with(authentication(authToken(principal)))
                        .param("period", "WEEK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("WEEK"))
                .andExpect(jsonPath("$.currentWindow.from").value("2026-06-15"))
                .andExpect(jsonPath("$.previousWindow.to").value("2026-06-11"))
                .andExpect(jsonPath("$.workout.completedWorkoutCount.delta").value(1.0))
                .andExpect(jsonPath("$.nutrition.totalCaloriesConsumed.deltaPct").value(33.3333));
    }

    @Test
    void shouldRejectInvalidComparePeriod() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/summary/compare")
                        .with(authentication(authToken(principal())))
                        .param("period", "DAY"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRequireAuthenticationForSummaryCompare() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/summary/compare")
                        .param("period", "WEEK"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectInvalidSeriesBucket() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/workouts/series")
                        .with(authentication(authToken(principal())))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-14")
                        .param("bucket", "YEAR"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBodyTrendsForAuthenticatedUser() throws Exception {
        CurrentUserPrincipal principal = principal();
        when(analyticsService.getBodyTrends(any(), any(), any(), any())).thenReturn(
                new AnalyticsBodyTrendsResponse(
                        LocalDate.parse("2026-06-01"),
                        LocalDate.parse("2026-06-14"),
                        "DAY",
                        List.of(new AnalyticsBodyTrendsResponse.BodyTrendPoint(
                                LocalDate.parse("2026-06-14"), 78.4, 19.8, 85.5, 24.7, -1.6, -0.2))));

        mockMvc.perform(get("/api/v1/analytics/body/trends")
                        .with(authentication(authToken(principal)))
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-14")
                        .param("bucket", "DAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].date").value("2026-06-14"))
                .andExpect(jsonPath("$.points[0].weightKg").value(78.4));
    }

    @Test
    void shouldReturnGoalProgressForAuthenticatedUser() throws Exception {
        CurrentUserPrincipal principal = principal();
        when(analyticsService.getGoalProgress(any(), any())).thenReturn(
                new AnalyticsGoalProgressResponse(List.of(
                        new AnalyticsGoalProgressResponse.GoalProgressView(
                                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                                "WEIGHT",
                                "ACTIVE",
                                72.0,
                                "kg",
                                LocalDate.parse("2026-08-01"),
                                78.4,
                                0.42,
                                48,
                                true,
                                Instant.parse("2026-06-14T08:00:00Z")))));

        mockMvc.perform(get("/api/v1/analytics/goals/progress")
                        .with(authentication(authToken(principal)))
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goals[0].goalType").value("WEIGHT"))
                .andExpect(jsonPath("$.goals[0].onTrack").value(true));
    }

    private UsernamePasswordAuthenticationToken authToken(CurrentUserPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "ROLE_" + principal.role()));
    }

    private CurrentUserPrincipal principal() {
        return new CurrentUserPrincipal(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "alice@example.com",
                "Alice",
                "USER");
    }
}
