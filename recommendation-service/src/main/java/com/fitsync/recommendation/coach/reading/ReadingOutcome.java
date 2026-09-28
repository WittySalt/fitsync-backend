package com.fitsync.recommendation.coach.reading;

/**
 * What became of one generation attempt for a stored reading row.
 *
 * <p>{@code PENDING} covers both "a generation is running right now" and "a generation was started
 * and never finished" (the process died, the executor dropped it); {@link CoachReadingService}
 * tells those apart by age rather than by a fourth state, because the row alone cannot know which one
 * it is.
 */
public enum ReadingOutcome {
    PENDING,
    ACCEPTED,
    REJECTED
}
