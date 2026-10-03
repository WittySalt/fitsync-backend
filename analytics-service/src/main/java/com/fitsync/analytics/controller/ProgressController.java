package com.fitsync.analytics.controller;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import com.fitsync.analytics.dto.ProgressFoodResponse;
import com.fitsync.analytics.dto.ProgressGoalResponse;
import com.fitsync.analytics.security.CurrentUserPrincipal;
import com.fitsync.analytics.service.ProgressFoodService;
import com.fitsync.analytics.service.ProgressGoalService;
import com.fitsync.analytics.service.ProgressWindow;
import com.fitsync.web.timezone.RequestTimezone;
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
 * The Goal and Food pages of Progress (progress-redesign contract sections 2.1 and 2.2). The caller comes
 * from the token only; the zone is the X-Timezone header, then the stored zone, then UTC.
 */
@RestController
@RequestMapping("/api/v1/analytics/progress")
@Tag(name = "Analytics progress", description = "Period figures of the Progress Goal and Food pages.")
public class ProgressController {

    private final ProgressGoalService progressGoalService;
    private final ProgressFoodService progressFoodService;

    public ProgressController(ProgressGoalService progressGoalService, ProgressFoodService progressFoodService) {
        this.progressGoalService = progressGoalService;
        this.progressFoodService = progressFoodService;
    }

    @GetMapping("/goal")
    @Operation(summary = "Get the Goal page of a period",
            description = "Weight, its 28-day projection, body measures, the energy pace and the other goals' "
                    + "adherence from from to to (inclusive, at most 370 days), each compared with the period "
                    + "of the same length right before it.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Goal page returned"),
            @ApiResponse(responseCode = "400", description = "from after to, a period over 370 days or a malformed date"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public ProgressGoalResponse goal(Authentication authentication,
                                     @Parameter(description = "Inclusive period start, ISO-8601.")
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @Parameter(description = "Inclusive period end, ISO-8601.")
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestTimezone Optional<ZoneId> zone) {
        ProgressWindow window = ProgressWindow.of(from, to);
        return progressGoalService.getGoal(currentPrincipal(authentication).userId(), window,
                zone.map(ZoneId::getId).orElse(null));
    }

    @GetMapping("/food")
    @Operation(summary = "Get the Food page of a period",
            description = "Eaten and protein a day, energy balance, macros by week, plate shares by calories, "
                    + "meal shares and logged days from from to to (inclusive, at most 370 days), compared with "
                    + "the period of the same length right before it.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Food page returned"),
            @ApiResponse(responseCode = "400", description = "from after to, a period over 370 days or a malformed date"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public ProgressFoodResponse food(Authentication authentication,
                                     @Parameter(description = "Inclusive period start, ISO-8601.")
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @Parameter(description = "Inclusive period end, ISO-8601.")
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestTimezone Optional<ZoneId> zone) {
        ProgressWindow window = ProgressWindow.of(from, to);
        return progressFoodService.getFood(currentPrincipal(authentication).userId(), window,
                zone.map(ZoneId::getId).orElse(null));
    }

    private CurrentUserPrincipal currentPrincipal(Authentication authentication) {
        return (CurrentUserPrincipal) authentication.getPrincipal();
    }
}
