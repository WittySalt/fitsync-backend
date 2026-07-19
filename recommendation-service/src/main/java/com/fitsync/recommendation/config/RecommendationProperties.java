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
        ai = ai == null ? new Ai(false, null, null, null, null, null, null) : ai;
    }

    /**
     * @param enabled      master switch for the AI tier; a narrative client is only
     *                     built when true
     * @param provider     which backend phrases the narrative: {@code anthropic} (the
     *                     Claude API via the SDK — needs {@code ANTHROPIC_API_KEY} in
     *                     the env) or {@code ollama} (a local/self-hosted open model —
     *                     no key at all)
     * @param model        model ID — configuration, never hard-coded (README §4.1
     *                     tiering: the weekly narrative defaults to the Sonnet tier;
     *                     for ollama use whatever is pulled locally, e.g. llama3.2:3b)
     * @param baseUrl      the ollama server URL; ignored by the anthropic provider
     * @param maxTokens    completion budget for one narrative
     * @param narrativeTtl how long a generated narrative stays fresh (README §5.2:
     *                     cached one week)
     * @param coachQuestionsPerHour per-user budget for the synchronous A4 coach Q&A —
     *                     the one AI surface the user waits on
     */
    public record Ai(
            boolean enabled,
            String provider,
            String model,
            String baseUrl,
            Integer maxTokens,
            Duration narrativeTtl,
            Integer coachQuestionsPerHour) {

        public static final String PROVIDER_ANTHROPIC = "anthropic";
        public static final String PROVIDER_OLLAMA = "ollama";

        private static final String DEFAULT_MODEL = "claude-sonnet-4-6";
        private static final String DEFAULT_BASE_URL = "http://localhost:11434";
        private static final Integer DEFAULT_MAX_TOKENS = 300;
        private static final Duration DEFAULT_NARRATIVE_TTL = Duration.ofDays(7);
        private static final Integer DEFAULT_COACH_QUESTIONS_PER_HOUR = 5;

        public Ai {
            provider = provider == null || provider.isBlank() ? PROVIDER_ANTHROPIC : provider;
            model = model == null || model.isBlank() ? DEFAULT_MODEL : model;
            baseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl;
            maxTokens = maxTokens == null ? DEFAULT_MAX_TOKENS : maxTokens;
            narrativeTtl = narrativeTtl == null ? DEFAULT_NARRATIVE_TTL : narrativeTtl;
            coachQuestionsPerHour = coachQuestionsPerHour == null
                    ? DEFAULT_COACH_QUESTIONS_PER_HOUR
                    : coachQuestionsPerHour;
        }
    }
}
