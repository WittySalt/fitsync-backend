package com.fitsync.analytics.controller;

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
import com.fitsync.analytics.dto.BodyTrendBucket;
import com.fitsync.analytics.dto.ComparePeriod;
import com.fitsync.analytics.security.CurrentUserPrincipal;
import com.fitsync.analytics.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
@Validated
@Tag(name = "Analytics", description = "User-scoped derived analytics and dashboard queries.")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Get dashboard analytics snapshot",
            description = "Returns the current authenticated user's dashboard summary.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard snapshot returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public AnalyticsDashboardResponse dashboard(Authentication authentication) {
        return analyticsService.getDashboard(currentPrincipal(authentication));
    }

    @GetMapping("/body/trends")
    @Operation(summary = "Get body trend series",
            description = "Returns derived weight and body-metric trend points for the authenticated user.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trend series returned"),
            @ApiResponse(responseCode = "400", description = "Invalid date range or bucket"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public AnalyticsBodyTrendsResponse bodyTrends(Authentication authentication,
                                                  @Parameter(description = "Inclusive range start date in ISO-8601 format.")
                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                  LocalDate from,
                                                  @Parameter(description = "Inclusive range end date in ISO-8601 format.")
                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                  LocalDate to,
                                                  @Parameter(description = "Aggregation bucket.")
                                                  @RequestParam(defaultValue = "DAY") BodyTrendBucket bucket) {
        return analyticsService.getBodyTrends(currentPrincipal(authentication), from, to, bucket);
    }

    @GetMapping("/workouts/summary")
    @Operation(summary = "Get workout summary",
            description = "Returns aggregate workout totals and streak state for the authenticated user.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workout summary returned"),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public AnalyticsWorkoutSummaryResponse workoutSummary(Authentication authentication,
                                                          @Parameter(description = "Inclusive range start date in ISO-8601 format.")
                                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                          LocalDate from,
                                                          @Parameter(description = "Inclusive range end date in ISO-8601 format.")
                                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                          LocalDate to) {
        return analyticsService.getWorkoutSummary(currentPrincipal(authentication), from, to);
    }

    @GetMapping("/workouts/series")
    @Operation(summary = "Get workout activity series",
            description = "Returns workout activity points for the authenticated user.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workout series returned"),
            @ApiResponse(responseCode = "400", description = "Invalid date range or bucket"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public AnalyticsWorkoutSeriesResponse workoutSeries(Authentication authentication,
                                                        @Parameter(description = "Inclusive range start date in ISO-8601 format.")
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                        LocalDate from,
                                                        @Parameter(description = "Inclusive range end date in ISO-8601 format.")
                                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                        LocalDate to,
                                                        @Parameter(description = "Aggregation bucket.")
                                                        @RequestParam(defaultValue = "DAY") BodyTrendBucket bucket) {
        return analyticsService.getWorkoutSeries(currentPrincipal(authentication), from, to, bucket);
    }

    @GetMapping("/activity/calendar")
    @Operation(summary = "Get activity calendar",
            description = "Returns per-day workout activity intensity for the authenticated user, suitable for a "
                    + "heatmap. Every day in the range is returned; days with no activity report zero counts.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Activity calendar returned"),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public AnalyticsActivityCalendarResponse activityCalendar(Authentication authentication,
                                                             @Parameter(description = "Inclusive range start date in ISO-8601 format.")
                                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                             LocalDate from,
                                                             @Parameter(description = "Inclusive range end date in ISO-8601 format.")
                                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                             LocalDate to) {
        return analyticsService.getActivityCalendar(currentPrincipal(authentication), from, to);
    }

    @GetMapping("/nutrition/summary")
    @Operation(summary = "Get nutrition summary",
            description = "Returns aggregate nutrition adherence metrics for the authenticated user.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nutrition summary returned"),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public AnalyticsNutritionSummaryResponse nutritionSummary(Authentication authentication,
                                                              @Parameter(description = "Inclusive range start date in ISO-8601 format.")
                                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                              LocalDate from,
                                                              @Parameter(description = "Inclusive range end date in ISO-8601 format.")
                                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                              LocalDate to) {
        return analyticsService.getNutritionSummary(currentPrincipal(authentication), from, to);
    }

    @GetMapping("/nutrition/series")
    @Operation(summary = "Get nutrition intake series",
            description = "Returns nutrition total points for the authenticated user.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nutrition series returned"),
            @ApiResponse(responseCode = "400", description = "Invalid date range or bucket"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public AnalyticsNutritionSeriesResponse nutritionSeries(Authentication authentication,
                                                            @Parameter(description = "Inclusive range start date in ISO-8601 format.")
                                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                            LocalDate from,
                                                            @Parameter(description = "Inclusive range end date in ISO-8601 format.")
                                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                            LocalDate to,
                                                            @Parameter(description = "Aggregation bucket.")
                                                            @RequestParam(defaultValue = "DAY") BodyTrendBucket bucket) {
        return analyticsService.getNutritionSeries(currentPrincipal(authentication), from, to, bucket);
    }

    @GetMapping("/summary/compare")
    @Operation(summary = "Get period-over-period summary comparison",
            description = "Returns current and previous period comparison metrics for the authenticated user.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Summary comparison returned"),
            @ApiResponse(responseCode = "400", description = "Invalid comparison period"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public AnalyticsCompareResponse summaryCompare(Authentication authentication,
                                                   @Parameter(description = "Comparison period.")
                                                   @RequestParam ComparePeriod period) {
        return analyticsService.getSummaryCompare(currentPrincipal(authentication), period);
    }

    @GetMapping("/goals/progress")
    @Operation(summary = "Get goal progress analytics",
            description = "Returns derived progress and status snapshots for the authenticated user's goals.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Goal progress returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public AnalyticsGoalProgressResponse goalProgress(Authentication authentication,
                                                      @Parameter(description = "Optional goal status filter such as ACTIVE or PAUSED.")
                                                      @RequestParam(required = false) String status) {
        return analyticsService.getGoalProgress(currentPrincipal(authentication), status);
    }

    private CurrentUserPrincipal currentPrincipal(Authentication authentication) {
        return (CurrentUserPrincipal) authentication.getPrincipal();
    }
}
