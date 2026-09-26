package com.fitsync.recommendation.coach.service;

import java.util.List;
import java.util.UUID;

import com.fitsync.recommendation.coach.dto.CoachMessageResponse;
import com.fitsync.recommendation.coach.entity.CoachTurnEntity;
import com.fitsync.recommendation.coach.entity.CoachTurnRole;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import com.fitsync.recommendation.coach.orchestrator.CoachOrchestrator;
import com.fitsync.recommendation.coach.orchestrator.CoachOutcome;
import org.springframework.stereotype.Service;

/**
 * Joins the conversation spine to the orchestrator.
 *
 * <p>Deliberately not transactional across the model call. The user's message is committed before
 * inference starts, so a model that times out leaves a thread with the question in it rather than
 * losing what the user typed. The answer and its telemetry are committed after.
 */
@Service
public class CoachConversationService {

    private final CoachThreadService threadService;
    private final CoachOrchestrator orchestrator;

    public CoachConversationService(CoachThreadService threadService, CoachOrchestrator orchestrator) {
        this.threadService = threadService;
        this.orchestrator = orchestrator;
    }

    public CoachMessageResponse send(UUID userId, UUID threadId, String bearerToken, String message) {
        // Committed first: whatever the model does next, the question is not lost.
        threadService.appendTurn(userId, threadId, CoachTurnRole.USER, message);

        // The assistant turn is created before the answer exists, because tool invocations are
        // children of a turn and the orchestrator records them as it goes.
        CoachTurnEntity answerTurn =
                threadService.appendTurn(userId, threadId, CoachTurnRole.ASSISTANT, "");

        CoachCallContext context = new CoachCallContext(userId, bearerToken, answerTurn.getTurnId());
        List<String> history = priorExchanges(threadId, answerTurn.getTurnId());

        CoachOutcome outcome = orchestrator.answer(context, message, history);

        threadService.replaceContent(answerTurn.getTurnId(), outcome.answer());
        threadService.recordTelemetry(
                answerTurn.getTurnId(),
                outcome.tier(),
                outcome.modelAvailable() ? null : "unavailable",
                null,
                null,
                outcome.latencyMs(),
                outcome.modelAvailable() ? "PASSED" : "MODEL_OFF");

        return new CoachMessageResponse(
                threadId, answerTurn.getTurnId(), outcome.answer(),
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
}
