package com.fitsync.recommendation.coach.entity;

/**
 * Which path produced the stored draft.
 *
 * <p>Recorded on every artifact because the three are not interchangeable. A user is told when a
 * session came from the rule engine rather than the model, and the ratio between these is the
 * number that says whether the model is earning its place in the product.
 */
public enum DraftSource {
    /** The model's first attempt passed validation. */
    MODEL,
    /** The model's first attempt failed and its repair passed. */
    MODEL_REPAIRED,
    /** Both model attempts failed, or the model was unavailable, and the rules built it. */
    RULE_FALLBACK
}
