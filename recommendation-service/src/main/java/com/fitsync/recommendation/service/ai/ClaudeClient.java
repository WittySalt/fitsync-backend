package com.fitsync.recommendation.service.ai;

/**
 * The narrow seam between the recommendation logic and the Claude API. A bean of this
 * type exists only when the AI tier is enabled ({@code fitsync.recommendation.ai.enabled}
 * + {@code ANTHROPIC_API_KEY}); callers must treat its absence as "AI tier off" and
 * fall back to rule/template output.
 */
public interface ClaudeClient {

    /**
     * One single-turn completion.
     *
     * @return the model's text
     * @throws RuntimeException on any API failure — callers degrade gracefully
     */
    String complete(String systemPrompt, String userPrompt);
}
