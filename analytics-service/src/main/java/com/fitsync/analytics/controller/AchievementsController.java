package com.fitsync.analytics.controller;

import java.time.LocalDate;

import com.fitsync.analytics.dto.AnalyticsAchievementsResponse;
import com.fitsync.analytics.dto.AnalyticsFootprintResponse;
import com.fitsync.analytics.security.CurrentUserPrincipal;
import com.fitsync.analytics.service.AchievementsService;
import com.fitsync.analytics.service.FootprintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The badges and the footprint of the Me page (me-achievements-spec.md, sections 2 and 5). The caller
 * comes from the token only; there is no way to read another user's badges.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@Tag(name = "Analytics achievements", description = "Badges and the lifetime footprint of the Me page.")
public class AchievementsController {

    private final AchievementsService achievementsService;
    private final FootprintService footprintService;

    public AchievementsController(AchievementsService achievementsService, FootprintService footprintService) {
        this.achievementsService = achievementsService;
        this.footprintService = footprintService;
    }

    @GetMapping("/achievements")
    @Operation(summary = "Get achievements",
            description = "The seventeen badges in four themes and their paths, each with its earned date, progress "
                    + "and evidence, plus the latest earned badge, the two closest next badges, retired badges the "
                    + "user earned before they were retired, and the training pace. A badge once earned stays "
                    + "earned.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Badges returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public AnalyticsAchievementsResponse achievements(Authentication authentication) {
        return achievementsService.getAchievements(currentPrincipal(authentication));
    }

    @GetMapping("/me/footprint")
    @Operation(summary = "Get the lifetime footprint",
            description = "One entry per day from the Monday on or before the member-since day (or the first "
                    + "logged day when since is not given) to the Sunday of the current week, saying whether the "
                    + "user trained and whether a meal was logged (a fast day counts), plus lifetime totals of "
                    + "workouts, logged days and badges.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Footprint returned"),
            @ApiResponse(responseCode = "400", description = "since in the future, over ten years back or malformed"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public AnalyticsFootprintResponse footprint(Authentication authentication,
                                                @Parameter(description = "The account's first day, ISO-8601, "
                                                        + "from the profile. Optional.")
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate since) {
        return footprintService.getFootprint(currentPrincipal(authentication), since);
    }

    private CurrentUserPrincipal currentPrincipal(Authentication authentication) {
        return (CurrentUserPrincipal) authentication.getPrincipal();
    }
}
