package com.fitsync.recommendation.coach.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The HTTP transport the coach's catalogue clients run on.
 *
 * <p>Separate from the clients themselves because a timeout is a property of the connection, not of
 * the catalogue being read, and because a client that builds its own request factory cannot be put
 * in front of a mock server: binding one to a builder is how Spring's own test support works, and
 * replacing the factory afterwards silently discards it. Keeping the transport here leaves the
 * clients testable without a running service.
 *
 * <p>The timeouts are deliberately short. A catalogue that is slow should become an observation the
 * model works around within a few seconds, rather than holding a turn open while the user waits.
 */
@Configuration
public class CoachHttpClientConfig {

    @Bean("coachRestClientBuilder")
    public RestClient.Builder coachRestClientBuilder(CoachProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) properties.catalogueTimeout().toMillis());
        factory.setReadTimeout((int) properties.catalogueTimeout().toMillis());
        return RestClient.builder().requestFactory(factory);
    }
}
