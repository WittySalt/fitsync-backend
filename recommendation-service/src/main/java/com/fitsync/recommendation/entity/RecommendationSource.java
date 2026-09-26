package com.fitsync.recommendation.entity;

/**
 * How a {@link RecommendationResultEntity} was produced, in decision-funnel order
 * (README §5.1): a deterministic {@code RULE}, a hand-written {@code TEMPLATE} filled
 * with rule numbers, or an {@code AI}-generated narrative.
 */
public enum RecommendationSource {
    RULE,
    TEMPLATE,
    AI
}
