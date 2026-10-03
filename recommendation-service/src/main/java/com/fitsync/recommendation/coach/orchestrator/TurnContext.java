package com.fitsync.recommendation.coach.orchestrator;

import java.time.LocalDate;
import java.util.List;

import com.fitsync.recommendation.coach.dto.CoachCardContext;
import com.fitsync.recommendation.coach.dto.CoachSurface;

/**
 * Where a turn was started from, and the reading it was asked about, already resolved.
 *
 * <p>The orchestrator never reads the {@code coach.reading} package itself: resolving a pinned
 * reading means calling out to workout-service and re-deriving facts, which belongs with the rest of
 * that read-model's logic, not inside the agent loop. {@link com.fitsync.recommendation.coach.service.CoachConversationService}
 * does that resolution and hands the orchestrator this already-flattened shape instead, so the two
 * stay decoupled the same way the orchestrator and the tool catalogue are only coupled through
 * {@link CoachCallContext} rather than each other's internals.
 *
 * @param surface       the page the user is on, or null when the request carried no context at all
 * @param pinnedReading the reading the user is asking about, resolved and matched to the id the
 *                      client sent, or null when there is none
 * @param recipeId      the id of the recipe page the question was asked on, already checked to be a
 *                      UUID, or null; the recipe itself is never carried here
 * @param card          the Progress card the question was asked on, already validated, or null; only
 *                      its kind, id and dates, never its figures
 */
public record TurnContext(CoachSurface surface, PinnedReading pinnedReading, String recipeId, CoachCardContext card) {

    public TurnContext(CoachSurface surface, PinnedReading pinnedReading) {
        this(surface, pinnedReading, null, null);
    }

    public TurnContext(CoachSurface surface, PinnedReading pinnedReading, String recipeId) {
        this(surface, pinnedReading, recipeId, null);
    }

    /** A context with a known surface and no pinned reading. */
    public static TurnContext ofSurface(CoachSurface surface) {
        return new TurnContext(surface, null, null, null);
    }

    /**
     * The reading's text and the facts it was built from, flattened out of {@code coach.reading}'s
     * own shapes so this package never has to import them.
     *
     * @param headline the reading's headline, or null when there is no reading text at all
     * @param body     the reading's body, or null when there is no reading text at all
     * @param facts    the measured facts the reading was built from; never empty when this record
     *                 exists at all, because {@code CoachReadingService} only ever returns a pinned
     *                 reading for at least two facts
     * @param period   the week the facts describe, or null when it is not known
     */
    public record PinnedReading(String headline, String body, List<PinnedFact> facts, PinnedPeriod period) {

        public PinnedReading {
            facts = facts == null ? List.of() : List.copyOf(facts);
        }

        public PinnedReading(String headline, String body, List<PinnedFact> facts) {
            this(headline, body, facts, null);
        }

        @Override
        public List<PinnedFact> facts() {
            return List.copyOf(facts);
        }
    }

    /**
     * The week a pinned reading describes. A reading describes the current week once it holds
     * enough sessions and the last full week before that, so the model has to be told which.
     *
     * @param currentWeek true for the current week so far, false for the last full week
     * @param start       the first day described
     * @param end         the last day described
     */
    public record PinnedPeriod(boolean currentWeek, LocalDate start, LocalDate end) {
    }

    /** One measured fact behind a pinned reading, numbered exactly as the reading cites it. */
    public record PinnedFact(int number, String label, String value, String unit, String note, String detail) {
    }
}
