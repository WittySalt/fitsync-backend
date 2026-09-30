package com.fitsync.recommendation.coach.controller;

import java.util.UUID;

import com.fitsync.recommendation.coach.reading.CoachReadingResponse;
import com.fitsync.recommendation.coach.reading.CoachReadingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The reading behind one page's insight panel: measured facts, always; the coach's short observation
 * over them, once one exists and has passed validation.
 *
 * <p>A GET, not a POST, and cheap by design. It never runs the model itself, only decides whether one
 * is owed and enqueues it if so, which is what lets a client poll this endpoint while
 * {@code WRITING} without either side needing a socket held open.
 */
@RestController
@RequestMapping("/api/v1/coach/readings")
@Validated
@Tag(name = "Coach readings", description = "The model's observation over one page's measured facts.")
public class CoachReadingController {

    /** Letters, digits and the characters an IANA zone id actually uses, up to 64 characters. */
    private static final String TIMEZONE_PATTERN = "^[A-Za-z0-9/_+-]{1,64}$";

    private final CoachReadingService readingService;

    public CoachReadingController(CoachReadingService readingService) {
        this.readingService = readingService;
    }

    @GetMapping("/workout")
    @Operation(summary = "The workout page's insight panel",
            description = "Four measured facts, built without a model, and a short reading over them. "
                    + "The reading is written by fixed rules by default; a model-written reading only "
                    + "ever replaces it once one has been generated for exactly these facts and has "
                    + "passed the same number and tone checks. The reading is never present unless "
                    + "status is READY; on the rare turn the fixed rules have nothing to say, the "
                    + "client sees WRITING while a model generation is in flight and is expected to "
                    + "poll this endpoint again shortly, or RESTING when none is.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Facts returned, with a reading when one is ready"),
            @ApiResponse(responseCode = "400", description = "Timezone is not a plausible IANA zone id"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "503", description = "The workout service could not be read")
    })
    public CoachReadingResponse workout(Authentication authentication,
                                        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                        @RequestParam(defaultValue = "UTC")
                                        @Pattern(regexp = TIMEZONE_PATTERN) String timezone) {
        UUID userId = CoachPrincipal.userId(authentication);
        String bearerToken = CoachPrincipal.bearerToken(authorization);
        return readingService.workoutReading(userId, bearerToken, timezone);
    }

    @GetMapping("/nutrition")
    @Operation(summary = "The nutrition page's insight panel",
            description = "Up to four measured facts about last week's protein and calories, built "
                    + "without a model, and a short reading over them, written and checked the same "
                    + "way as the workout page's. Status is EMPTY when no full week was logged.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Facts returned, with a reading when one is ready"),
            @ApiResponse(responseCode = "400", description = "Timezone is not a plausible IANA zone id"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "503", description = "The nutrition service could not be read")
    })
    public CoachReadingResponse nutrition(Authentication authentication,
                                          @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                          @RequestParam(defaultValue = "UTC")
                                          @Pattern(regexp = TIMEZONE_PATTERN) String timezone) {
        UUID userId = CoachPrincipal.userId(authentication);
        String bearerToken = CoachPrincipal.bearerToken(authorization);
        return readingService.nutritionReading(userId, bearerToken, timezone);
    }

    @GetMapping("/progress")
    @Operation(summary = "The progress page's insight panel",
            description = "Up to four measured facts about the weight over the last 28 days, its "
                    + "weekly rate, the goal and calories, built without a model, and a short reading "
                    + "over them, written and checked the same way as the workout page's. Status is "
                    + "EMPTY when there is no weight yet.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Facts returned, with a reading when one is ready"),
            @ApiResponse(responseCode = "400", description = "Timezone is not a plausible IANA zone id"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "503", description = "The user service could not be read")
    })
    public CoachReadingResponse progress(Authentication authentication,
                                         @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                         @RequestParam(defaultValue = "UTC")
                                         @Pattern(regexp = TIMEZONE_PATTERN) String timezone) {
        UUID userId = CoachPrincipal.userId(authentication);
        String bearerToken = CoachPrincipal.bearerToken(authorization);
        return readingService.progressReading(userId, bearerToken, timezone);
    }
}
