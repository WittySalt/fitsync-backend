package com.fitsync.recommendation.coach.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import com.fitsync.recommendation.coach.config.CoachProperties;
import com.fitsync.recommendation.coach.dto.CoachMessageContext;
import com.fitsync.recommendation.coach.dto.CoachMessageResponse;
import com.fitsync.recommendation.coach.dto.CoachSurface;
import com.fitsync.recommendation.coach.entity.CoachTurnEntity;
import com.fitsync.recommendation.coach.entity.CoachTurnRole;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import com.fitsync.recommendation.coach.orchestrator.CoachOrchestrator;
import com.fitsync.recommendation.coach.orchestrator.CoachOutcome;
import com.fitsync.recommendation.coach.orchestrator.CoachProgressListener;
import com.fitsync.recommendation.coach.orchestrator.TurnContext;
import com.fitsync.recommendation.coach.reading.CoachReadingService;
import com.fitsync.recommendation.coach.reading.ReadingSurface;
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
    private final CoachReadingService readingService;
    private final CoachProperties properties;
    private final ExecutorService turnExecutor;

    public CoachConversationService(CoachThreadService threadService,
                                    CoachOrchestrator orchestrator,
                                    CoachReadingService readingService,
                                    CoachProperties properties,
                                    @Qualifier("coachTurnExecutor") ExecutorService turnExecutor) {
        this.threadService = threadService;
        this.orchestrator = orchestrator;
        this.readingService = readingService;
        this.properties = properties;
        this.turnExecutor = turnExecutor;
    }

    /** Sends a message with no page context, exactly as every caller from before this existed does. */
    public CoachMessageResponse send(UUID userId, UUID threadId, String bearerToken, String message) {
        return send(userId, threadId, bearerToken, message, null);
    }

    /**
     * Sends a message, naming the page the user was on and, when it is the workout page and a
     * reading is pinned, the reading they are asking about.
     */
    public CoachMessageResponse send(UUID userId, UUID threadId, String bearerToken, String message,
                                     CoachMessageContext context) {
        PendingTurn pending = beginTurn(userId, threadId, message);
        return finishTurn(pending, bearerToken, message, context, CoachProgressListener.NONE);
    }

    /**
     * Runs a turn, streaming each step to the caller as it happens, with no page context.
     *
     * <p>The turn is opened on this thread on purpose. Appending the user's message is what checks
     * that the thread belongs to the caller, so someone else's thread is a plain 404 rather than a
     * stream that opens and then reports an error, and the question is committed before the
     * response starts exactly as it is on the plain path.
     */
    public SseEmitter stream(UUID userId, UUID threadId, String bearerToken, String message) {
        return stream(userId, threadId, bearerToken, message, null);
    }

    /** Runs a turn, streaming each step to the caller as it happens, with the page context attached. */
    public SseEmitter stream(UUID userId, UUID threadId, String bearerToken, String message,
                             CoachMessageContext context) {
        PendingTurn pending = beginTurn(userId, threadId, message);

        SseEmitter emitter = new SseEmitter(properties.streamTimeout().toMillis());
        SseProgressStream stream = new SseProgressStream(emitter);

        turnExecutor.execute(() -> {
            try {
                stream.sendAnswer(finishTurn(pending, bearerToken, message, context, stream));
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
                                            CoachMessageContext messageContext,
                                            CoachProgressListener listener) {
        ResolvedContext resolved = resolveTurnContext(pending.userId(), bearerToken, messageContext);

        CoachCallContext context = new CoachCallContext(pending.userId(), bearerToken, pending.turnId(),
                messageContext == null ? null : messageContext.timezone());
        CoachOutcome outcome = orchestrator.answer(context, message, pending.history(), resolved.turnContext(), listener);

        threadService.replaceContent(pending.turnId(), outcome.answer());
        threadService.recordTelemetry(
                pending.turnId(),
                outcome.tier(),
                outcome.modelAvailable() ? null : "unavailable",
                null,
                null,
                outcome.latencyMs(),
                outcome.guardrailVerdict());

        return new CoachMessageResponse(
                pending.threadId(), pending.turnId(), outcome.answer(),
                outcome.tier(), outcome.toolsUsed(), outcome.latencyMs(), resolved.pinnedReadingId());
    }

    /**
     * Turns the client's page name and opaque reading id into what the orchestrator actually needs:
     * the surface, and — only for a page that shows a reading, only when a reading id was sent, and only when it
     * resolves to something real — the reading's text and facts.
     *
     * <p>Resolution runs off the request thread on the streaming path exactly like the rest of
     * {@code finishTurn}, and a failure here is never allowed to fail the turn: the coach still owes
     * the user an answer even when the page it was asked about could not be re-read. Nothing about
     * the failure reaches the log, not even its message, which could echo the token from a
     * well-meaning exception; only the exception's class name does.
     */
    private ResolvedContext resolveTurnContext(UUID userId, String bearerToken, CoachMessageContext messageContext) {
        if (messageContext == null) {
            return new ResolvedContext(null, null);
        }

        TurnContext.PinnedReading pinnedReading = null;
        String pinnedReadingId = null;
        ReadingSurface readingSurface = readingSurfaceOf(messageContext.surface());
        if (readingSurface != null && messageContext.readingId() != null) {
            try {
                Optional<CoachReadingService.PinnedReading> resolved = readingService.pinnedReading(readingSurface,
                        userId, bearerToken, messageContext.timezoneOrDefault(), messageContext.readingId());
                if (resolved.isPresent()) {
                    CoachReadingService.PinnedReading reading = resolved.get();
                    pinnedReading = new TurnContext.PinnedReading(reading.headline(), reading.body(),
                            toPinnedFacts(reading), toPinnedPeriod(reading));
                    pinnedReadingId = reading.readingId();
                }
            } catch (RuntimeException ex) {
                LOG.warn("event=coach_pinned_reading_resolution_failed reason={}",
                        String.valueOf(ex.getClass().getSimpleName()).replace("\r", "_").replace("\n", "_"));
            }
        }
        return new ResolvedContext(new TurnContext(messageContext.surface(), pinnedReading, messageContext.recipeId()),
                pinnedReadingId);
    }

    /** The pages that show a reading; every other page has nothing a message could be pinned to. */
    private static ReadingSurface readingSurfaceOf(CoachSurface surface) {
        if (surface == null) {
            return null;
        }
        return switch (surface) {
            case WORKOUT -> ReadingSurface.WORKOUT;
            case NUTRITION -> ReadingSurface.NUTRITION;
            case PROGRESS -> ReadingSurface.PROGRESS;
            case HOME, PROFILE -> null;
        };
    }

    private static TurnContext.PinnedPeriod toPinnedPeriod(CoachReadingService.PinnedReading reading) {
        // The prompt describes the period as a week, so a reading over any other span is left without one.
        if (!reading.describesAWeek() || reading.periodStart() == null || reading.periodEnd() == null) {
            return null;
        }
        return new TurnContext.PinnedPeriod(
                reading.describesCurrentWeek(), reading.periodStart(), reading.periodEnd());
    }

    private static List<TurnContext.PinnedFact> toPinnedFacts(CoachReadingService.PinnedReading reading) {
        return reading.facts().stream()
                .map(fact -> new TurnContext.PinnedFact(
                        fact.number(), fact.label(), fact.value(), fact.unit(), fact.note(), fact.detail()))
                .toList();
    }

    /** What a client's context resolved to: what the orchestrator sees, and what the response reports. */
    private record ResolvedContext(TurnContext turnContext, String pinnedReadingId) {
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
