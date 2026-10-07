package com.fitsync.recommendation.service.ai;

/**
 * The narrow seam between the recommendation logic and the narrative model. A bean of
 * this type exists only when the AI tier is enabled ({@code fitsync.recommendation.ai.enabled});
 * callers must treat its absence as "AI tier off" and fall back to rule/template output.
 * {@code fitsync.recommendation.ai.provider} selects the implementation — the Claude API
 * ({@code anthropic}, key via {@code ANTHROPIC_API_KEY}), a local open model
 * ({@code ollama}, no key) or Amazon Bedrock ({@code bedrock}, IAM credentials, no key).
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
