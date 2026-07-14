package com.fitsync.recommendation.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param ruleResultTtl how long a rule-computed {@code recommendation_result} stays
 *                      fresh before the read path recomputes it even without a new
 *                      event (rules are cheap, so this is a freshness bound, not a
 *                      cost lever)
 */
@ConfigurationProperties(prefix = "fitsync.recommendation")
public record RecommendationProperties(
        Duration ruleResultTtl) {

    private static final Duration DEFAULT_RULE_RESULT_TTL = Duration.ofHours(24);

    public RecommendationProperties {
        ruleResultTtl = ruleResultTtl == null ? DEFAULT_RULE_RESULT_TTL : ruleResultTtl;
    }
}
