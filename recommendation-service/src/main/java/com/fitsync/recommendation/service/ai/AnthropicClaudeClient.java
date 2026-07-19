package com.fitsync.recommendation.service.ai;

import java.util.List;
import java.util.stream.Collectors;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.fitsync.recommendation.config.RecommendationProperties;

/**
 * Claude API adapter (README §5.2): the key comes from {@code ANTHROPIC_API_KEY} via
 * the SDK's {@code fromEnv()} (X-4 — never from source or the database), the model ID
 * is configuration (§4.1 tiering), and the stable system prompt carries a
 * {@code cache_control} breakpoint so the cached prefix bills at ~0.1×.
 *
 * Wired by {@link com.fitsync.recommendation.config.NarrativeClientConfiguration} when
 * the AI tier is enabled with the {@code anthropic} provider (the default).
 */
public class AnthropicClaudeClient implements ClaudeClient {

    private final AnthropicClient client;
    private final RecommendationProperties.Ai aiProperties;

    public AnthropicClaudeClient(RecommendationProperties properties) {
        this(AnthropicOkHttpClient.fromEnv(), properties);
    }

    AnthropicClaudeClient(AnthropicClient client, RecommendationProperties properties) {
        this.client = client;
        this.aiProperties = properties.ai();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(aiProperties.model())
                .maxTokens(aiProperties.maxTokens().longValue())
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(systemPrompt)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                .addUserMessage(userPrompt)
                .build();
        Message message = client.messages().create(params);
        return message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining())
                .strip();
    }
}
