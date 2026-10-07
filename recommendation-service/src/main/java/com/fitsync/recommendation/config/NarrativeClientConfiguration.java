package com.fitsync.recommendation.config;

import com.fitsync.recommendation.service.ai.AnthropicClaudeClient;
import com.fitsync.recommendation.service.ai.BedrockClaudeClient;
import com.fitsync.recommendation.service.ai.ClaudeClient;
import com.fitsync.recommendation.service.ai.OllamaClaudeClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Builds the one {@link ClaudeClient} bean for the AI tier — or none at all when the
 * tier is disabled (the default), which is how the coach knows to
 * stay silent. {@code fitsync.recommendation.ai.provider} picks the backend:
 * {@code anthropic} (default — the Claude API, key via {@code ANTHROPIC_API_KEY}),
 * {@code ollama} (local open model, no key) or {@code bedrock} (Amazon Bedrock, IAM
 * credentials, no key). Everything downstream of the seam —
 * async queue, hash gate, TTL, silent fallback — is provider-agnostic.
 */
@Configuration
@ConditionalOnProperty(prefix = "fitsync.recommendation.ai", name = "enabled", havingValue = "true")
public class NarrativeClientConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "fitsync.recommendation.ai", name = "provider",
            havingValue = RecommendationProperties.Ai.PROVIDER_ANTHROPIC, matchIfMissing = true)
    ClaudeClient anthropicClaudeClient(RecommendationProperties properties) {
        return new AnthropicClaudeClient(properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "fitsync.recommendation.ai", name = "provider",
            havingValue = RecommendationProperties.Ai.PROVIDER_OLLAMA)
    ClaudeClient ollamaClaudeClient(RestClient.Builder restClientBuilder, RecommendationProperties properties) {
        return new OllamaClaudeClient(restClientBuilder, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "fitsync.recommendation.ai", name = "provider",
            havingValue = RecommendationProperties.Ai.PROVIDER_BEDROCK)
    ClaudeClient bedrockClaudeClient(RecommendationProperties properties) {
        return new BedrockClaudeClient(properties);
    }
}
