package com.fitsync.recommendation.coach.controller;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.recommendation.coach.reading.WeeklyReviewService;
import com.fitsync.recommendation.coach.reading.WeeklyReviewView;
import com.fitsync.web.timezone.RequestTimezone;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The weekly reviews on the Progress page. A GET, cheap after the first visit, and safe to poll while
 * a week is {@code WRITING}. The user comes from the token, and each user only ever sees their own
 * weeks.
 */
@RestController
@RequestMapping("/api/v1/coach/reviews")
@Validated
@Tag(name = "Coach reviews", description = "One short review per Monday week, written from the Progress figures.")
public class CoachReviewController {

    private final WeeklyReviewService reviewService;

    public CoachReviewController(WeeklyReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    @Operation(summary = "Weekly reviews, newest first",
            description = "One review per Monday week in the caller's zone (X-Timezone, then the stored zone, "
                    + "then UTC), the current week first and written so far. Each review cites numbered facts "
                    + "taken from the Training, Goal and Food figures of its week, and each fact names its card. "
                    + "A week with fewer than two facts is NONE. A week that ended is written once and kept; "
                    + "the current week is written again when its figures change.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The reviews, newest first"),
            @ApiResponse(responseCode = "400", description = "weeks is outside 1 to 26"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public List<WeeklyReviewView> reviews(Authentication authentication,
                                          @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                          @RequestTimezone Optional<ZoneId> zone,
                                          @RequestParam(defaultValue = "8") @Min(1) @Max(WeeklyReviewService.MAX_WEEKS)
                                          int weeks) {
        UUID userId = CoachPrincipal.userId(authentication);
        String bearerToken = CoachPrincipal.bearerToken(authorization);
        return reviewService.reviews(userId, bearerToken, zone.map(ZoneId::getId).orElse(null), weeks);
    }
}
