package com.fitsync.recommendation.service.rules;

import java.util.Optional;

import com.fitsync.recommendation.entity.RecommendationContextEntity;

/**
 * A Tier-1 local rule (README §4.1): a pure function over the per-user context.
 * Returns empty when the rule does not apply (missing inputs, nothing to say) —
 * empty is a normal outcome, not an error. Rules never do I/O and never call AI.
 */
public interface Rule {

    /** The {@link RecommendationTypes} value this rule produces. */
    String type();

    Optional<RuleOutcome> evaluate(RecommendationContextEntity context);
}
