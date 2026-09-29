package com.fitsync.recommendation.coach.repository;

import java.time.Instant;

/**
 * The columns {@link CoachTurnRepository#findQualityRows} projects for the admin quality report.
 *
 * <p>Deliberately not the entity itself: {@code content} is never selected, so the report cannot
 * hold a turn's text in memory even briefly, which is the privacy rule for that page enforced at the
 * query rather than by filtering a response afterwards.
 */
public record CoachTurnQualityRow(
        Integer tier,
        String model,
        Integer latencyMs,
        String guardrailVerdict,
        Instant dateTimeCreated) {
}
