package com.fitsync.recommendation.service.ai;

import java.time.Duration;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fitsync.recommendation.config.RecommendationProperties;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

/**
 * Amazon Bedrock adapter, the AWS-hosted sibling of {@link AnthropicClaudeClient}. It talks
 * to Bedrock's model-agnostic Converse API, so the same code serves Claude and Amazon Nova;
 * the model is configuration (a Bedrock model or inference-profile ID such as
 * {@code global.anthropic.claude-haiku-4-5-20251001-v1:0}).
 *
 * No key exists anywhere in this path. The SDK's default chains supply credentials and
 * region: the EC2 instance role and its region in the cloud, the developer's AWS profile
 * and {@code AWS_REGION} locally. The SDK retries throttling with backoff on its own; the
 * call timeout below bounds the whole attempt so a slow model hands over to the rule text
 * instead of holding the caller.
 *
 * Wired by {@link com.fitsync.recommendation.config.NarrativeClientConfiguration} when
 * the AI tier is enabled with the {@code bedrock} provider.
 */
public class BedrockClaudeClient implements ClaudeClient {

    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(60);

    private final BedrockRuntimeClient client;
    private final RecommendationProperties.Ai aiProperties;

    public BedrockClaudeClient(RecommendationProperties properties) {
        this(BedrockRuntimeClient.builder()
                .overrideConfiguration(config -> config.apiCallTimeout(CALL_TIMEOUT))
                .build(), properties);
    }

    BedrockClaudeClient(BedrockRuntimeClient client, RecommendationProperties properties) {
        this.client = client;
        this.aiProperties = properties.ai();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        ConverseRequest request = ConverseRequest.builder()
                .modelId(aiProperties.model())
                .system(SystemContentBlock.fromText(systemPrompt))
                .messages(Message.builder()
                        .role(ConversationRole.USER)
                        .content(ContentBlock.fromText(userPrompt))
                        .build())
                .inferenceConfig(InferenceConfiguration.builder()
                        .maxTokens(aiProperties.maxTokens())
                        .build())
                .build();
        ConverseResponse response = client.converse(request);
        if (response.output() == null || response.output().message() == null) {
            return "";
        }
        return response.output().message().content().stream()
                .map(ContentBlock::text)
                .filter(Objects::nonNull)
                .collect(Collectors.joining())
                .strip();
    }
}
