package com.fitsync.recommendation.coach.reading;

import java.time.LocalDate;
import java.util.List;

/**
 * One page's insight panel: the facts, always; the model's reading, only once it exists and passed
 * validation for exactly these facts.
 *
 * <p>{@code readingId} is the first sixteen hex characters of the SHA-256 hash of the facts it was
 * written from — a stable, opaque identifier a client can use to notice the reading changed without
 * re-reading the whole body, and short enough to log without it reading as a real hash.
 */
public record CoachReadingResponse(
        ReadingSurface surface,
        ReadingStatus status,
        String readingId,
        String period,
        LocalDate periodStart,
        LocalDate periodEnd,
        List<ReadingFact> facts,
        ModelReadingView reading,
        List<TrainingInsight.LiftProgress> lifts) {

    public CoachReadingResponse {
        facts = facts == null ? List.of() : List.copyOf(facts);
        lifts = lifts == null ? List.of() : List.copyOf(lifts);
    }
}
