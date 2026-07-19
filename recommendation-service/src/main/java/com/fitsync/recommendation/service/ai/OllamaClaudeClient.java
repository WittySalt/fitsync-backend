package com.fitsync.recommendation.service.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fitsync.recommendation.config.RecommendationProperties;
import org.springframework.web.client.RestClient;

/**
 * Ollama adapter — the zero-cost sibling of {@link AnthropicClaudeClient} for running
 * the A1 narrative on a local/self-hosted open model (e.g. {@code llama3.2:3b}). Talks
 * to Ollama's native {@code /api/chat} endpoint; no API key exists anywhere in this
 * path, and {@code maxTokens} maps onto Ollama's {@code num_predict}.
 *
 * Wired by {@link com.fitsync.recommendation.config.NarrativeClientConfiguration} when
 * the AI tier is enabled with the {@code ollama} provider.
 */
public class OllamaClaudeClient implements ClaudeClient {

    private final RestClient restClient;
    private final RecommendationProperties.Ai aiProperties;

    public OllamaClaudeClient(RestClient.Builder restClientBuilder, RecommendationProperties properties) {
        this.aiProperties = properties.ai();
        this.restClient = restClientBuilder.baseUrl(aiProperties.baseUrl()).build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        ChatRequest request = new ChatRequest(
                aiProperties.model(),
                List.of(new ChatMessage("system", systemPrompt), new ChatMessage("user", userPrompt)),
                false,
                new ChatOptions(aiProperties.maxTokens()));
        ChatResponse response = restClient.post()
                .uri("/api/chat")
                .body(request)
                .retrieve()
                .body(ChatResponse.class);
        if (response == null || response.message() == null || response.message().content() == null) {
            return "";
        }
        return response.message().content().strip();
    }

    record ChatRequest(String model, List<ChatMessage> messages, boolean stream, ChatOptions options) {
    }

    record ChatMessage(String role, String content) {
    }

    record ChatOptions(@JsonProperty("num_predict") Integer numPredict) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(ChatMessage message) {
    }
}
