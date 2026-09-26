package com.fitsync.recommendation.coach.controller;

import java.util.List;
import java.util.UUID;

import com.fitsync.recommendation.coach.dto.CoachThreadDetailView;
import com.fitsync.recommendation.coach.dto.CoachThreadView;
import com.fitsync.recommendation.coach.dto.CreateCoachThreadRequest;
import com.fitsync.recommendation.coach.service.CoachThreadService;
import com.fitsync.recommendation.security.CurrentUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conversation state for the coach: threads and their turns.
 *
 * <p>Sending a message is deliberately not here. It belongs with the orchestrator, because it
 * streams and calls tools; this controller owns only the state those answers are written into,
 * which keeps the persistence surface testable without a model in the loop.
 *
 * <p>Every read is scoped to the caller by querying for ownership rather than loading a thread and
 * comparing afterwards, so another user's thread is indistinguishable from one that does not exist.
 */
@RestController
@RequestMapping("/api/v1/coach/threads")
@Tag(name = "Coach threads", description = "Conversations with the coach, and the trace behind each answer.")
public class CoachThreadController {

    private final CoachThreadService threadService;

    public CoachThreadController(CoachThreadService threadService) {
        this.threadService = threadService;
    }

    @GetMapping
    @Operation(summary = "List my live threads, most recently used first",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Threads returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public List<CoachThreadView> list(Authentication authentication) {
        return threadService.listThreads(currentUserId(authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a thread",
            description = "The title is optional; a thread names itself from its opening question.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Thread created"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public CoachThreadView create(Authentication authentication,
                                  @Valid @RequestBody(required = false) CreateCoachThreadRequest request) {
        String title = request == null ? null : request.title();
        return threadService.createThread(currentUserId(authentication), title);
    }

    @GetMapping("/{threadId}")
    @Operation(summary = "Read a thread with all of its turns and traces",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Thread returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "No such thread for this user")
    })
    public CoachThreadDetailView get(Authentication authentication, @PathVariable UUID threadId) {
        return threadService.getThread(currentUserId(authentication), threadId);
    }

    @DeleteMapping("/{threadId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archive a thread",
            description = "Archiving rather than deleting keeps the telemetry on its turns available "
                    + "to the evaluation harness after a user tidies their list away.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Thread archived"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "No such thread for this user")
    })
    public void archive(Authentication authentication, @PathVariable UUID threadId) {
        threadService.archiveThread(currentUserId(authentication), threadId);
    }

    private static UUID currentUserId(Authentication authentication) {
        return ((CurrentUserPrincipal) authentication.getPrincipal()).userId();
    }
}
