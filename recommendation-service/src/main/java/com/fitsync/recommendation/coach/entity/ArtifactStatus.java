package com.fitsync.recommendation.coach.entity;

/**
 * Where a draft got to.
 *
 * <p>Discarded drafts are kept rather than deleted. What a user rejected is the most direct signal
 * the evaluation harness has about whether the coach is proposing things worth doing, and it cannot
 * be recovered once the row is gone.
 */
public enum ArtifactStatus {
    DRAFT,
    ACCEPTED,
    DISCARDED
}
