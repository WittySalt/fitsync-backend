/**
 * Tier-1 local rule engine (RC-2/RC-3): a registry of {@code Rule} implementations,
 * each {@code (RecommendationContext) -> Optional<Recommendation>}, plus the
 * hand-written narrative template library (RC-5). Output is cached with source {@code RULE}.
 */
package com.fitsync.recommendation.service.rules;
