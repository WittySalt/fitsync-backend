package com.fitsync.recommendation.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fitsync.recommendation.config.RecommendationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;

class BedrockClaudeClientTests {

    private static final String MODEL = "global.anthropic.claude-haiku-4-5-20251001-v1:0";

    private BedrockRuntimeClient bedrock;
    private BedrockClaudeClient client;

    @BeforeEach
    void setUp() {
        bedrock = mock(BedrockRuntimeClient.class);
        RecommendationProperties properties = new RecommendationProperties(null,
                new RecommendationProperties.Ai(true, "bedrock", MODEL, null, 300, null, null));
        client = new BedrockClaudeClient(bedrock, properties);
    }

    @Test
    void sendsBothPromptsWithTheConfiguredModelAndBudgetAndReturnsTheStrippedReply() {
        when(bedrock.converse(any(ConverseRequest.class))).thenReturn(reply(
                ContentBlock.fromText("  A strong week "), ContentBlock.fromText("overall.  ")));

        assertThat(client.complete("You are a coach.", "Facts: {}")).isEqualTo("A strong week overall.");

        ArgumentCaptor<ConverseRequest> sent = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(bedrock).converse(sent.capture());
        ConverseRequest request = sent.getValue();
        assertThat(request.modelId()).isEqualTo(MODEL);
        assertThat(request.inferenceConfig().maxTokens()).isEqualTo(300);
        assertThat(request.system()).singleElement()
                .satisfies(block -> assertThat(block.text()).isEqualTo("You are a coach."));
        assertThat(request.messages()).singleElement().satisfies(message -> {
            assertThat(message.role()).isEqualTo(ConversationRole.USER);
            assertThat(message.content()).singleElement()
                    .satisfies(block -> assertThat(block.text()).isEqualTo("Facts: {}"));
        });
    }

    @Test
    void returnsAnEmptyReplyWhenTheModelSendsNoText() {
        when(bedrock.converse(any(ConverseRequest.class))).thenReturn(ConverseResponse.builder().build());

        assertThat(client.complete("system", "user")).isEmpty();
    }

    @Test
    void surfacesThrottlingAsARuntimeExceptionForTheCallerToDegradeOn() {
        when(bedrock.converse(any(ConverseRequest.class)))
                .thenThrow(ThrottlingException.builder().message("Too many requests").build());

        assertThatThrownBy(() -> client.complete("system", "user")).isInstanceOf(RuntimeException.class);
    }

    private static ConverseResponse reply(ContentBlock... blocks) {
        return ConverseResponse.builder()
                .output(ConverseOutput.fromMessage(Message.builder()
                        .role(ConversationRole.ASSISTANT)
                        .content(blocks)
                        .build()))
                .build();
    }
}
