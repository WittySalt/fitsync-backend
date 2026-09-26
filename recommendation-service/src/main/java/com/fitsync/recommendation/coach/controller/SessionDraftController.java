package com.fitsync.recommendation.coach.controller;

import java.util.List;
import java.util.UUID;

import com.fitsync.recommendation.coach.dto.DraftMetricsView;
import com.fitsync.recommendation.coach.dto.DraftSessionRequest;
import com.fitsync.recommendation.coach.dto.SessionDraftView;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import com.fitsync.recommendation.coach.service.SessionDraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session drafting, which is a surface of its own rather than a tool inside the chat loop.
 *
 * <p>Drafting calls a model, and running it as a tool would put one inference inside another's
 * loop: slower, harder to bound, and harder to explain in a trace. Keeping it separate also matches
 * how it is used, because a user opening Plan Studio to get a session for today is not having a
 * conversation. A chat turn can still link to a draft by its identifier.
 */
@RestController
@RequestMapping("/api/v1/coach/session-drafts")
@Tag(name = "Coach drafts", description = "Training sessions the coach proposes, and how they were produced.")
public class SessionDraftController {

    private final SessionDraftService draftService;

    public SessionDraftController(SessionDraftService draftService) {
        this.draftService = draftService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Draft a training session",
            description = "Gathers candidates from the exercise catalogue, asks the model, validates "
                    + "the result against five domain rules, gives the model one chance to fix what "
                    + "it got wrong, and falls back to the rule engine if it cannot. A draft is "
                    + "always either valid or absent; it is never plausible and wrong.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Drafted"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "503", description = "Neither the model nor the rules could build one")
    })
    public SessionDraftView draft(Authentication authentication,
                                  @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                  @RequestParam(required = false) UUID threadId,
                                  @Valid @RequestBody(required = false) DraftSessionRequest request) {
        // The caller's own token goes to the catalogue and the questionnaire, so the draft is built
        // from exactly what this user is allowed to see.
        CoachCallContext call = new CoachCallContext(
                CoachPrincipal.userId(authentication),
                CoachPrincipal.bearerToken(authorization),
                null);
        return draftService.draft(call, request == null ? null : request.request(), threadId);
    }

    @GetMapping
    @Operation(summary = "List this user's session drafts", security = @SecurityRequirement(name = "bearerAuth"))
    public List<SessionDraftView> list(Authentication authentication) {
        return draftService.list(CoachPrincipal.userId(authentication));
    }

    @GetMapping("/{artifactId}")
    @Operation(summary = "Read one session draft", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(responseCode = "404", description = "No such draft for this user")
    })
    public SessionDraftView get(Authentication authentication, @PathVariable UUID artifactId) {
        return draftService.get(CoachPrincipal.userId(authentication), artifactId);
    }

    @GetMapping("/metrics")
    @Operation(summary = "How this user's drafts were produced",
            description = "The three numbers that say whether the model is earning its place: how "
                    + "many drafts it produced at all, how many passed validation first time, and "
                    + "how many needed the repair round. Scoped to the caller; the evaluation "
                    + "harness aggregates across users from the same columns.",
            security = @SecurityRequirement(name = "bearerAuth"))
    public DraftMetricsView metrics(Authentication authentication) {
        SessionDraftService.DraftGenerationMetrics metrics =
                draftService.metrics(CoachPrincipal.userId(authentication));
        return new DraftMetricsView(
                metrics.total(),
                metrics.model(),
                metrics.modelRepaired(),
                metrics.ruleFallback(),
                metrics.modelSuccessRate(),
                metrics.firstPassRate(),
                metrics.repairRate());
    }
}
