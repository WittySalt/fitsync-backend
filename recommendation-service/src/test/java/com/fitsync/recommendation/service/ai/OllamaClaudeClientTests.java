package com.fitsync.recommendation.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fitsync.recommendation.config.RecommendationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OllamaClaudeClientTests {

    private MockRestServiceServer server;
    private OllamaClaudeClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RecommendationProperties properties = new RecommendationProperties(null,
                new RecommendationProperties.Ai(true, "ollama", "llama3.2:3b", "http://localhost:11434",
                        300, null, null));
        client = new OllamaClaudeClient(builder, properties);
    }

    @Test
    void postsBothPromptsToTheChatEndpointAndReturnsTheStrippedReply() {
        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("llama3.2:3b"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.options.num_predict").value(300))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("You are a coach."))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("Facts: {}"))
                .andRespond(withSuccess(
                        "{\"message\":{\"role\":\"assistant\",\"content\":\"  A strong week overall.  \"},\"done\":true}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.complete("You are a coach.", "Facts: {}")).isEqualTo("A strong week overall.");
        server.verify();
    }

    @Test
    void surfacesApiFailuresAsRuntimeExceptionsForTheCallerToDegradeOn() {
        server.expect(requestTo("http://localhost:11434/api/chat")).andRespond(withServerError());

        assertThatThrownBy(() -> client.complete("system", "user")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void treatsAMissingMessageAsABlankNarrative() {
        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andRespond(withSuccess("{\"done\":true}", MediaType.APPLICATION_JSON));

        assertThat(client.complete("system", "user")).isEmpty();
    }
}
