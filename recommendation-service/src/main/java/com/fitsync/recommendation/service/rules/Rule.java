package com.fitsync.recommendation.service.rules;

import java.util.Optional;

import com.fitsync.recommendation.domain.TodayIntake;
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

    /**
     * As {@link #evaluate(RecommendationContextEntity)}, with today's intake read from the diary by
     * the caller. Only the rules that speak about today use it; the read is the caller's, so a rule
     * still does no I/O.
     */
    default Optional<RuleOutcome> evaluate(RecommendationContextEntity context, TodayIntake today) {
        return evaluate(context);
    }
}
