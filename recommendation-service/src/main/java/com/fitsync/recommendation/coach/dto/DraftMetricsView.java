package com.fitsync.recommendation.coach.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How session drafting has been going.
 *
 * <p>Counts and rates together on purpose. A first-pass rate of 0.75 over four drafts and the same
 * rate over four hundred are different claims, and returning only the rate would let the first be
 * read as the second.
 *
 * @param modelSuccessRate drafts a model produced, whether or not a repair was needed
 * @param firstPassRate    drafts that passed validation on the model's first attempt
 * @param repairRate       of the drafts a model produced, how many needed the repair round
 */
@Schema(description = "Draft generation quality: how many drafts came from where.")
public record DraftMetricsView(
        long total,
        long model,
        long modelRepaired,
        long ruleFallback,
        double modelSuccessRate,
        double firstPassRate,
        double repairRate) {
}
