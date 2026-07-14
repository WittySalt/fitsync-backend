package com.fitsync.recommendation.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param ruleResultTtl how long a rule-computed {@code recommendation_result} stays
 *                      fresh before the read path recomputes it even without a new
 *                      event (rules are cheap, so this is a freshness bound, not a
 *                      cost lever)
 * @param ai            the Tier-2 AI knobs (README §5.2) — disabled by default so the
 *                      service is fully functional on rules/templates alone
 */
@ConfigurationProperties(prefix = "fitsync.recommendation")
public record RecommendationProperties(
        Duration ruleResultTtl,
        Ai ai) {

    private static final Duration DEFAULT_RULE_RESULT_TTL = Duration.ofHours(24);

    public RecommendationProperties {
        ruleResultTtl = ruleResultTtl == null ? DEFAULT_RULE_RESULT_TTL : ruleResultTtl;
        ai = ai == null ? new Ai(false, null, null, null) : ai;
    }

    /**
     * @param enabled      master switch for the AI tier; the SDK client is only built
     *                     when true (and needs {@code ANTHROPIC_API_KEY} in the env)
     * @param model        model ID — configuration, never hard-coded (README §4.1
     *                     tiering: the weekly narrative defaults to the Sonnet tier)
     * @param maxTokens    completion budget for one narrative
     * @param narrativeTtl how long a generated narrative stays fresh (README §5.2:
     *                     cached one week)
     */
    public record Ai(
            boolean enabled,
            String model,
            Integer maxTokens,
            Duration narrativeTtl) {

        private static final String DEFAULT_MODEL = "claude-sonnet-4-6";
        private static final Integer DEFAULT_MAX_TOKENS = 300;
        private static final Duration DEFAULT_NARRATIVE_TTL = Duration.ofDays(7);

        public Ai {
            model = model == null || model.isBlank() ? DEFAULT_MODEL : model;
            maxTokens = maxTokens == null ? DEFAULT_MAX_TOKENS : maxTokens;
            narrativeTtl = narrativeTtl == null ? DEFAULT_NARRATIVE_TTL : narrativeTtl;
        }
    }
}
