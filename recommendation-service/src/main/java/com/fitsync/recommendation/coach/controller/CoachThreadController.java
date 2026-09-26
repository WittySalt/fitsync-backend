package com.fitsync.recommendation.coach.controller;

import java.util.List;
import java.util.UUID;

import com.fitsync.recommendation.coach.dto.CoachThreadDetailView;
import com.fitsync.recommendation.coach.dto.CoachThreadView;
import com.fitsync.recommendation.coach.dto.CoachMessageResponse;
import com.fitsync.recommendation.coach.dto.CreateCoachThreadRequest;
import com.fitsync.recommendation.coach.dto.SendCoachMessageRequest;
import com.fitsync.recommendation.coach.service.CoachConversationService;
import com.fitsync.recommendation.coach.service.CoachThreadService;
import com.fitsync.recommendation.security.CurrentUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Conversation state for the coach: threads and their turns.
 *
 * <p>Sending a message runs the orchestrator, which gathers what it needs through its tools and
 * records each call against the answering turn. That makes the trace available as soon as the
 * answer is.
 *
 * <p>Every read is scoped to the caller by querying for ownership rather than loading a thread and
 * comparing afterwards, so another user's thread is indistinguishable from one that does not exist.
 */
@RestController
@RequestMapping("/api/v1/coach/threads")
@Tag(name = "Coach threads", description = "Conversations with the coach, and the trace behind each answer.")
public class CoachThreadController {

    private final CoachThreadService threadService;
    private final CoachConversationService conversationService;

    public CoachThreadController(CoachThreadService threadService,
                                 CoachConversationService conversationService) {
        this.threadService = threadService;
        this.conversationService = conversationService;
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

    @PostMapping("/{threadId}/messages")
    @Operation(summary = "Send a message and get the coach's answer",
            description = "Answers in one response. The orchestrator gathers what it needs through "
                    + "its tools, each of which is recorded against the answering turn, so the trace "
                    + "is available immediately afterwards. Use the streaming variant to watch that "
                    + "gathering happen.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Answered"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "No such thread for this user")
    })
    public CoachMessageResponse send(Authentication authentication,
                                     @PathVariable UUID threadId,
                                     @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                     @Valid @RequestBody SendCoachMessageRequest request) {
        // The caller's own token is forwarded to the services that own the catalogues, so the coach
        // reads with exactly the caller's authority. Taken from the header rather than the security
        // context because the orchestrator runs tools off the request thread.
        return conversationService.send(
                currentUserId(authentication), threadId, stripBearer(authorization), request.message());
    }

    @PostMapping(path = "/{threadId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Send a message and watch the coach work",
            description = "Same turn as the plain endpoint, with each step streamed as it happens: "
                    + "tool_started and tool_finished per tool, thinking before each model call, then "
                    + "a single answer event carrying the same body the plain endpoint returns. "
                    + "Progress rather than tokens, because for an assistant that spends most of a "
                    + "turn reading data, seeing which data it read is the more useful of the two. "
                    + "POST rather than GET, so the message travels in a body; clients read it with "
                    + "fetch rather than EventSource.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Event stream opened"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "No such thread for this user")
    })
    public SseEmitter stream(Authentication authentication,
                             @PathVariable UUID threadId,
                             @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                             @Valid @RequestBody SendCoachMessageRequest request) {
        return conversationService.stream(
                currentUserId(authentication), threadId, stripBearer(authorization), request.message());
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

    private static String stripBearer(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7)
                : authorization;
    }

    private static UUID currentUserId(Authentication authentication) {
        return ((CurrentUserPrincipal) authentication.getPrincipal()).userId();
    }
}
