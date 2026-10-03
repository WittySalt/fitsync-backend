package com.fitsync.analytics.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import java.time.LocalDate;

import com.fitsync.analytics.dto.AnalyticsAchievementsResponse;
import com.fitsync.analytics.dto.AnalyticsActivityCalendarResponse;
import com.fitsync.analytics.dto.AnalyticsBodyTrendsResponse;
import com.fitsync.analytics.dto.AnalyticsCompareResponse;
import com.fitsync.analytics.dto.AnalyticsDashboardResponse;
import com.fitsync.analytics.dto.AnalyticsEnergyWeeklyResponse;
import com.fitsync.analytics.dto.AnalyticsGoalProgressResponse;
import com.fitsync.analytics.dto.AnalyticsNutritionSeriesResponse;
import com.fitsync.analytics.dto.AnalyticsNutritionSummaryResponse;
import com.fitsync.analytics.dto.AnalyticsStreaksResponse;
import com.fitsync.analytics.dto.AnalyticsWeeklyReportResponse;
import com.fitsync.analytics.dto.AnalyticsWorkoutSeriesResponse;
import com.fitsync.analytics.dto.AnalyticsWorkoutSummaryResponse;
import com.fitsync.analytics.security.CurrentUserPrincipal;
import com.fitsync.analytics.security.JwtService;
import com.fitsync.analytics.service.AnalyticsEnergyService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
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

    @MockBean
    private AnalyticsEnergyService analyticsEnergyService;

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
                new AnalyticsDashboardResponse.BodySummary(78.4, 19.8, 24.7, -1.6, LocalDate.parse("2026-06-14"),
                        85.5, LocalDate.parse("2026-06-12")),
                new AnalyticsDashboardResponse.WorkoutSummary(1, 4, 205, 3, 2, 90, 3),
                new AnalyticsDashboardResponse.NutritionSummary(1860, 2100, 132.5, 150.0),
                List.of(new AnalyticsDashboardResponse.GoalProgressSummary(
                        goalId, "WEIGHT", 75.0, "kg", LocalDate.parse("2026-08-01"), 77.0, 0.42, true, false,
                        Instant.parse("2026-06-14T07:00:00Z"))));

        when(analyticsService.getDashboard(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/analytics/dashboard")
                        .with(authentication(authToken(principal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Asia/Singapore"))
                .andExpect(jsonPath("$.body.latestWaistCm").value(85.5))
                .andExpect(jsonPath("$.body.waistMeasuredAt").value("2026-06-12"))
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
                        new AnalyticsCompareResponse.MetricComparison(12.0, 6.0, 6.0, 100.0),
                        new AnalyticsCompareResponse.DaysLogged(3, 2)),
                new AnalyticsCompareResponse.NutritionComparison(
                        new AnalyticsCompareResponse.MetricComparison(4000.0, 3000.0, 1000.0, 33.3333),
                        new AnalyticsCompareResponse.MetricComparison(260.0, 200.0, 60.0, 30.0),
                        new AnalyticsCompareResponse.MetricComparison(420.0, 300.0, 120.0, 40.0),
                        new AnalyticsCompareResponse.MetricComparison(130.0, 100.0, 30.0, 30.0),
                        new AnalyticsCompareResponse.DaysLogged(4, 4),
                        new AnalyticsCompareResponse.AveragePerLoggedDay(
                                new AnalyticsCompareResponse.MetricComparison(1000.0, 750.0, 250.0, 33.3333),
                                new AnalyticsCompareResponse.MetricComparison(65.0, 50.0, 15.0, 30.0),
                                new AnalyticsCompareResponse.MetricComparison(105.0, 75.0, 30.0, 40.0),
                                new AnalyticsCompareResponse.MetricComparison(32.5, 25.0, 7.5, 30.0))),
                true));

        mockMvc.perform(get("/api/v1/analytics/summary/compare")
                        .with(authentication(authToken(principal)))
                        .param("period", "WEEK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("WEEK"))
                .andExpect(jsonPath("$.currentWindow.from").value("2026-06-15"))
                .andExpect(jsonPath("$.previousWindow.to").value("2026-06-11"))
                .andExpect(jsonPath("$.workout.completedWorkoutCount.delta").value(1.0))
                .andExpect(jsonPath("$.nutrition.totalCaloriesConsumed.deltaPct").value(33.3333))
                .andExpect(jsonPath("$.currentToDate").value(true))
                .andExpect(jsonPath("$.workout.daysLogged.current").value(3))
                .andExpect(jsonPath("$.nutrition.averagePerLoggedDay.calories.current").value(1000.0));
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
    void shouldRequireAuthenticationForStreaks() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/streaks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnStreaksForAuthenticatedUser() throws Exception {
        CurrentUserPrincipal principal = principal();
        when(analyticsService.getStreaks(any())).thenReturn(new AnalyticsStreaksResponse(
                6,
                14,
                LocalDate.parse("2026-03-02"),
                LocalDate.parse("2026-03-15"),
                LocalDate.parse("2026-07-07"),
                "Asia/Singapore"));

        mockMvc.perform(get("/api/v1/analytics/streaks")
                        .with(authentication(authToken(principal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreakDays").value(6))
                .andExpect(jsonPath("$.bestStreakDays").value(14))
                .andExpect(jsonPath("$.bestStreakFrom").value("2026-03-02"))
                .andExpect(jsonPath("$.bestStreakTo").value("2026-03-15"))
                .andExpect(jsonPath("$.lastActiveDate").value("2026-07-07"))
                .andExpect(jsonPath("$.timezone").value("Asia/Singapore"));
    }

    @Test
    void shouldRequireAuthenticationForAchievements() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/achievements"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnAchievementsForAuthenticatedUser() throws Exception {
        CurrentUserPrincipal principal = principal();
        when(analyticsService.getAchievements(any())).thenReturn(new AnalyticsAchievementsResponse(List.of(
                new AnalyticsAchievementsResponse.AchievementView(
                        "FIRST_WORKOUT", "First workout", true, Instant.parse("2026-05-01T10:00:00Z"), 1.0),
                new AnalyticsAchievementsResponse.AchievementView(
                        "STREAK_7", "7 days in a row", false, null, 0.86))));

        mockMvc.perform(get("/api/v1/analytics/achievements")
                        .with(authentication(authToken(principal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.achievements[0].code").value("FIRST_WORKOUT"))
                .andExpect(jsonPath("$.achievements[0].unlocked").value(true))
                .andExpect(jsonPath("$.achievements[0].unlockedAt").value("2026-05-01T10:00:00Z"))
                .andExpect(jsonPath("$.achievements[1].code").value("STREAK_7"))
                .andExpect(jsonPath("$.achievements[1].unlocked").value(false))
                .andExpect(jsonPath("$.achievements[1].progress").value(0.86));
    }

    @Test
    void shouldRequireAuthenticationForWeeklyReport() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/reports/weekly"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnWeeklyReportForAuthenticatedUser() throws Exception {
        CurrentUserPrincipal principal = principal();
        when(analyticsService.getWeeklyReport(any(), any())).thenReturn(new AnalyticsWeeklyReportResponse(
                LocalDate.parse("2026-07-06"),
                LocalDate.parse("2026-07-12"),
                4,
                190,
                1450,
                2050,
                5,
                -0.4,
                new AnalyticsWeeklyReportResponse.WeekOverWeekDelta(1, 32)));

        mockMvc.perform(get("/api/v1/analytics/reports/weekly")
                        .with(authentication(authToken(principal)))
                        .param("weekStart", "2026-07-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-07-06"))
                .andExpect(jsonPath("$.to").value("2026-07-12"))
                .andExpect(jsonPath("$.workoutCount").value(4))
                .andExpect(jsonPath("$.workoutMinutes").value(190))
                .andExpect(jsonPath("$.caloriesBurned").value(1450))
                .andExpect(jsonPath("$.avgDailyCaloriesConsumed").value(2050))
                .andExpect(jsonPath("$.proteinTargetHitDays").value(5))
                .andExpect(jsonPath("$.weightDeltaKg").value(-0.4))
                .andExpect(jsonPath("$.vsPreviousWeek.workoutCount").value(1))
                .andExpect(jsonPath("$.vsPreviousWeek.workoutMinutes").value(32));
    }

    @Test
    void shouldRejectMalformedWeekStart() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/reports/weekly")
                        .with(authentication(authToken(principal())))
                        .param("weekStart", "not-a-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void energyWeeklyRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/energy/weekly"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void energyWeeklyUsesTheCallerFromTheTokenOnly() throws Exception {
        CurrentUserPrincipal principal = principal();
        LocalDate monday = LocalDate.parse("2026-09-28");
        when(analyticsEnergyService.getWeekly(eq(principal.userId()), eq(monday), eq("Asia/Singapore")))
                .thenReturn(new AnalyticsEnergyWeeklyResponse(monday, monday.plusDays(6), "TOO_FEW_DAYS", 1, 1700,
                        true, 1.2, null, null, null, List.of(), List.of()));

        mockMvc.perform(get("/api/v1/analytics/energy/weekly")
                        .with(authentication(authToken(principal)))
                        .param("weekStart", "2026-09-28")
                        .header("X-Timezone", "Asia/Singapore")
                        .param("userId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TOO_FEW_DAYS"))
                .andExpect(jsonPath("$.bmrKcal").value(1700))
                .andExpect(jsonPath("$.bmrEstimated").value(true))
                .andExpect(jsonPath("$.restingFactor").value(1.2));
        verify(analyticsEnergyService).getWeekly(principal.userId(), monday, "Asia/Singapore");
    }

    @Test
    void energyWeeklyRejectsMalformedWeekStartAndIgnoresAnInvalidTimezoneHeader() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/energy/weekly")
                        .with(authentication(authToken(principal())))
                        .param("weekStart", "not-a-date"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/analytics/energy/weekly")
                        .with(authentication(authToken(principal())))
                        .header("X-Timezone", "x".repeat(65)))
                .andExpect(status().isOk());
        verify(analyticsEnergyService).getWeekly(any(), isNull(), isNull());
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
                                false,
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
