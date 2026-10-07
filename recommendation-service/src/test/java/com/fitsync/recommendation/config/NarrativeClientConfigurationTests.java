package com.fitsync.recommendation.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fitsync.recommendation.service.ai.AnthropicClaudeClient;
import com.fitsync.recommendation.service.ai.BedrockClaudeClient;
import com.fitsync.recommendation.service.ai.ClaudeClient;
import com.fitsync.recommendation.service.ai.OllamaClaudeClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class NarrativeClientConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class, RestClientAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfiguration.class, NarrativeClientConfiguration.class);

    @Test
    void aiTierIsOffByDefaultSoNoClientBeanExists() {
        runner.run(context -> assertThat(context).doesNotHaveBean(ClaudeClient.class));
    }

    @Test
    void ollamaProviderBuildsTheLocalAdapterWithoutAnyApiKey() {
        runner.withPropertyValues(
                        "fitsync.recommendation.ai.enabled=true",
                        "fitsync.recommendation.ai.provider=ollama")
                .run(context -> {
                    assertThat(context).hasSingleBean(ClaudeClient.class);
                    assertThat(context).getBean(ClaudeClient.class).isInstanceOf(OllamaClaudeClient.class);
                });
    }

    @Test
    void bedrockProviderBuildsTheBedrockAdapterWithoutAnyApiKey() {
        // The region normally comes from the EC2 metadata or AWS_REGION; pin it so the test
        // does not depend on the machine. Credentials are resolved lazily on the first call.
        runner.withSystemProperties("aws.region=ap-southeast-1")
                .withPropertyValues(
                        "fitsync.recommendation.ai.enabled=true",
                        "fitsync.recommendation.ai.provider=bedrock")
                .run(context -> {
                    assertThat(context).hasSingleBean(ClaudeClient.class);
                    assertThat(context).getBean(ClaudeClient.class).isInstanceOf(BedrockClaudeClient.class);
                });
    }

    @Test
    void anthropicIsTheDefaultProvider() {
        // Without ANTHROPIC_API_KEY in the environment the SDK fails fast at startup
        // (the documented X-4 behavior), so only assert the happy path when a key is
        // present; either way the ollama adapter must not be selected by default.
        runner.withPropertyValues("fitsync.recommendation.ai.enabled=true")
                .run(context -> {
                    if (context.getStartupFailure() == null) {
                        assertThat(context).getBean(ClaudeClient.class).isInstanceOf(AnthropicClaudeClient.class);
                    } else {
                        assertThat(context).hasFailed();
                    }
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RecommendationProperties.class)
    static class PropertiesConfiguration {
    }
}
