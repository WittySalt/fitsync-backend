package com.fitsync.recommendation.coach.orchestrator;

import java.util.List;

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
 */
public record TurnContext(CoachSurface surface, PinnedReading pinnedReading) {

    /** A context with a known surface and no pinned reading. */
    public static TurnContext ofSurface(CoachSurface surface) {
        return new TurnContext(surface, null);
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
     */
    public record PinnedReading(String headline, String body, List<PinnedFact> facts) {

        public PinnedReading {
            facts = facts == null ? List.of() : List.copyOf(facts);
        }

        @Override
        public List<PinnedFact> facts() {
            return List.copyOf(facts);
        }
    }

    /** One measured fact behind a pinned reading, numbered exactly as the reading cites it. */
    public record PinnedFact(int number, String label, String value, String unit, String note, String detail) {
    }
}
