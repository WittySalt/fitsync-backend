package com.fitsync.recommendation.coach.controller;

import java.time.LocalDate;

import com.fitsync.recommendation.coach.dto.CoachQualityResponse;
import com.fitsync.recommendation.coach.service.CoachQualityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The numbers behind the admin coach-quality page: how assistant answers were classified by the
 * guardrail, how long they took, which tools ran, and how insight-panel readings landed, for one
 * date range.
 *
 * <p>Restricted to ADMIN by {@code SecurityConfig}, which matches every path under
 * {@code /api/v1/internal/**} before a request reaches here. No caller identity is threaded through
 * to the service: this report never returns anything scoped to a user, so there is nothing for a
 * principal to authorize beyond the role check already done.
 */
@RestController
@RequestMapping("/api/v1/internal/coach")
@Tag(name = "Coach quality (internal)", description = "Admin-only aggregates over guardrail outcomes.")
public class CoachQualityController {

    private final CoachQualityService qualityService;

    public CoachQualityController(CoachQualityService qualityService) {
        this.qualityService = qualityService;
    }

    @GetMapping("/quality")
    @Operation(summary = "Coach answer quality for a date range",
            description = "Defaults to the last 7 days in UTC. The range is inclusive of both dates "
                    + "and capped at 92 days. Counts, rule codes, tool names, model names and "
                    + "durations only — never a user id, thread id, turn id or message text.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aggregates returned"),
            @ApiResponse(responseCode = "400", description = "Invalid date range or timezone"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin")
    })
    public CoachQualityResponse quality(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String timezone) {
        return qualityService.quality(from, to, timezone);
    }
}
