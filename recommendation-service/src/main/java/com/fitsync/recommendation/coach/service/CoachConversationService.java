package com.fitsync.recommendation.coach.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import com.fitsync.recommendation.coach.config.CoachProperties;
import com.fitsync.recommendation.coach.dto.CoachMessageResponse;
import com.fitsync.recommendation.coach.entity.CoachTurnEntity;
import com.fitsync.recommendation.coach.entity.CoachTurnRole;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import com.fitsync.recommendation.coach.orchestrator.CoachOrchestrator;
import com.fitsync.recommendation.coach.orchestrator.CoachOutcome;
import com.fitsync.recommendation.coach.orchestrator.CoachProgressListener;
import com.fitsync.recommendation.coach.stream.SseProgressStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Joins the conversation spine to the orchestrator.
 *
 * <p>Deliberately not transactional across the model call. The user's message is committed before
 * inference starts, so a model that times out leaves a thread with the question in it rather than
 * losing what the user typed. The answer and its telemetry are committed after.
 *
 * <p>The streaming and plain paths run the same turn. Only the listener differs, so a streamed
 * answer and a plain one cannot drift apart, and a client that cannot stream loses the progress
 * view rather than the feature.
 */
@Service
public class CoachConversationService {

    private static final Logger LOG = LoggerFactory.getLogger(CoachConversationService.class);

    private final CoachThreadService threadService;
    private final CoachOrchestrator orchestrator;
    private final CoachProperties properties;
    private final ExecutorService turnExecutor;

    public CoachConversationService(CoachThreadService threadService,
                                    CoachOrchestrator orchestrator,
                                    CoachProperties properties,
                                    @Qualifier("coachTurnExecutor") ExecutorService turnExecutor) {
        this.threadService = threadService;
        this.orchestrator = orchestrator;
        this.properties = properties;
        this.turnExecutor = turnExecutor;
    }

    public CoachMessageResponse send(UUID userId, UUID threadId, String bearerToken, String message) {
        PendingTurn pending = beginTurn(userId, threadId, message);
        return finishTurn(pending, bearerToken, message, CoachProgressListener.NONE);
    }

    /**
     * Runs a turn, streaming each step to the caller as it happens.
     *
     * <p>The turn is opened on this thread on purpose. Appending the user's message is what checks
     * that the thread belongs to the caller, so someone else's thread is a plain 404 rather than a
     * stream that opens and then reports an error, and the question is committed before the
     * response starts exactly as it is on the plain path.
     */
    public SseEmitter stream(UUID userId, UUID threadId, String bearerToken, String message) {
        PendingTurn pending = beginTurn(userId, threadId, message);

        SseEmitter emitter = new SseEmitter(properties.streamTimeout().toMillis());
        SseProgressStream stream = new SseProgressStream(emitter);

        turnExecutor.execute(() -> {
            try {
                stream.sendAnswer(finishTurn(pending, bearerToken, message, stream));
                stream.complete();
            } catch (RuntimeException ex) {
                // The turn is already persisted up to wherever it got, so the client is told the
                // turn failed and the detail stays in the log where it is useful.
                LOG.warn("Streamed coach turn failed for turn {}", pending.turnId(), ex);
                stream.sendFailure();
            }
        });

        return emitter;
    }

    /**
     * Opens a turn: the user's message and the empty assistant turn its tool invocations will hang
     * from, both committed before any inference starts.
     */
    private PendingTurn beginTurn(UUID userId, UUID threadId, String message) {
        threadService.appendTurn(userId, threadId, CoachTurnRole.USER, message);
        CoachTurnEntity answerTurn =
                threadService.appendTurn(userId, threadId, CoachTurnRole.ASSISTANT, "");
        return new PendingTurn(
                userId, threadId, answerTurn.getTurnId(),
                priorExchanges(threadId, answerTurn.getTurnId()));
    }

    private CoachMessageResponse finishTurn(PendingTurn pending,
                                            String bearerToken,
                                            String message,
                                            CoachProgressListener listener) {
        CoachCallContext context = new CoachCallContext(pending.userId(), bearerToken, pending.turnId());
        CoachOutcome outcome = orchestrator.answer(context, message, pending.history(), listener);

        threadService.replaceContent(pending.turnId(), outcome.answer());
        threadService.recordTelemetry(
                pending.turnId(),
                outcome.tier(),
                outcome.modelAvailable() ? null : "unavailable",
                null,
                null,
                outcome.latencyMs(),
                outcome.modelAvailable() ? "PASSED" : "MODEL_OFF");

        return new CoachMessageResponse(
                pending.threadId(), pending.turnId(), outcome.answer(),
                outcome.tier(), outcome.toolsUsed(), outcome.latencyMs());
    }

    /**
     * Prior turns rendered for the prompt, excluding the empty assistant turn just created and any
     * tool observations, which the orchestrator re-gathers for itself rather than replaying.
     */
    private List<String> priorExchanges(UUID threadId, UUID currentTurnId) {
        return threadService.contextWindow(threadId).stream()
                .filter(turn -> !turn.getTurnId().equals(currentTurnId))
                .filter(turn -> turn.getRole() != CoachTurnRole.TOOL)
                .filter(turn -> !turn.getContent().isBlank())
                .map(turn -> (turn.getRole() == CoachTurnRole.USER ? "User: " : "You: ") + turn.getContent())
                .toList();
    }

    /** A turn that has been opened and committed but not yet answered. */
    private record PendingTurn(UUID userId, UUID threadId, UUID turnId, List<String> history) {
    }
}
