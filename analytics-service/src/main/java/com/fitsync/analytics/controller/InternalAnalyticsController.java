package com.fitsync.analytics.controller;

import java.time.LocalDate;
import java.util.UUID;

import com.fitsync.analytics.dto.AnalyticsRebuildResponse;
import com.fitsync.analytics.service.AnalyticsRebuildService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/analytics")
@Validated
@Tag(name = "Internal Analytics", description = "Admin-only maintenance endpoints for analytics projections.")
public class InternalAnalyticsController {

    private final AnalyticsRebuildService analyticsRebuildService;

    public InternalAnalyticsController(AnalyticsRebuildService analyticsRebuildService) {
        this.analyticsRebuildService = analyticsRebuildService;
    }

    @PostMapping("/users/{userId}/dashboard/rebuild")
    @Operation(summary = "Rebuild dashboard snapshot",
            description = "Admin-only targeted rebuild for one user's dashboard snapshot.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dashboard snapshot rebuilt"),
            @ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public AnalyticsRebuildResponse rebuildDashboard(@PathVariable UUID userId) {
        return analyticsRebuildService.rebuildDashboard(userId);
    }

    @PostMapping("/users/{userId}/nutrition/rebuild")
    @Operation(summary = "Rebuild nutrition projection for a date",
            description = "Admin-only targeted rebuild for one user's nutrition aggregates on a single date.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Nutrition projection rebuilt"),
            @ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public AnalyticsRebuildResponse rebuildNutrition(@PathVariable UUID userId,
                                                     @Parameter(description = "Projection date to rebuild in ISO-8601 format.")
                                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                     LocalDate date) {
        return analyticsRebuildService.rebuildNutrition(userId, date);
    }

    @PostMapping("/users/{userId}/workouts/rebuild")
    @Operation(summary = "Rebuild workout projection for a date",
            description = "Admin-only targeted rebuild for one user's workout aggregates on a single date.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workout projection rebuilt"),
            @ApiResponse(responseCode = "403", description = "Admin role required")
    })
    public AnalyticsRebuildResponse rebuildWorkouts(@PathVariable UUID userId,
                                                    @Parameter(description = "Projection date to rebuild in ISO-8601 format.")
                                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                    LocalDate date) {
        return analyticsRebuildService.rebuildWorkouts(userId, date);
    }
}
