package com.fitsync.recommendation.controller;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.dto.CoachAnswerResponse;
import com.fitsync.recommendation.dto.CoachQuestionRequest;
import com.fitsync.recommendation.dto.RecommendationResponse;
import com.fitsync.recommendation.security.CurrentUserPrincipal;
import com.fitsync.recommendation.service.RecommendationResolver;
import com.fitsync.recommendation.service.ai.CoachQaService;
import com.fitsync.web.error.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * RC-7 — the read API (README §8). Every read is served through the resolver funnel:
 * cache when fresh, the synchronous rule stage otherwise. Generation is never inline
 * beyond the millisecond-level rules; the AI tier stays behind the cache (RC-6).
 */
@RestController
@RequestMapping("/api/v1/recommendations")
@Tag(name = "Recommendations", description = "Cached, user-scoped recommendations from the rule/template tiers.")
public class RecommendationController {

    private final RecommendationResolver resolver;
    private final CoachQaService coachQaService;
    private final ObjectMapper objectMapper;

    public RecommendationController(RecommendationResolver resolver,
                                    CoachQaService coachQaService,
                                    ObjectMapper objectMapper) {
        this.resolver = resolver;
        this.coachQaService = coachQaService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @Operation(summary = "Get current recommendations",
            description = "Returns the authenticated user's cached recommendations, recomputing the rule tier "
                    + "first when the underlying context changed.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recommendations returned (possibly empty)"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public List<RecommendationResponse> recommendations(Authentication authentication) {
        return resolver.resolve(currentUserId(authentication)).stream()
                .map(result -> RecommendationResponse.from(result, objectMapper))
                .toList();
    }

    @GetMapping("/{type}")
    @Operation(summary = "Get one recommendation type",
            description = "Returns a single recommendation type for the authenticated user.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recommendation returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "No recommendation of this type for the user")
    })
    public RecommendationResponse recommendation(@PathVariable("type") String type,
                                                 Authentication authentication) {
        return resolver.resolve(currentUserId(authentication), normalize(type))
                .map(result -> RecommendationResponse.from(result, objectMapper))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No recommendation of type " + normalize(type) + " for the current user"));
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Recompute recommendations",
            description = "Recomputes the rule tier synchronously. The future AI tier is enqueued "
                    + "asynchronously from here, gated by change detection — never inline.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Refresh performed"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public void refresh(Authentication authentication) {
        resolver.refresh(currentUserId(authentication));
    }

    @PostMapping("/coach/ask")
    @Operation(summary = "Ask the coach one question",
            description = "A4 — a synchronous, single-turn answer grounded only on the user's pre-computed "
                    + "facts. Budgeted per user per hour; unavailable when the AI tier is off.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Answer returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "429", description = "Hourly question budget exhausted"),
            @ApiResponse(responseCode = "503", description = "AI tier disabled or model unavailable")
    })
    public CoachAnswerResponse ask(Authentication authentication,
                                   @Valid @RequestBody CoachQuestionRequest request) {
        return new CoachAnswerResponse(
                coachQaService.ask(currentUserId(authentication), request.question()));
    }

    private static java.util.UUID currentUserId(Authentication authentication) {
        return ((CurrentUserPrincipal) authentication.getPrincipal()).userId();
    }

    private static String normalize(String type) {
        return type.toUpperCase(java.util.Locale.ROOT);
    }
}
