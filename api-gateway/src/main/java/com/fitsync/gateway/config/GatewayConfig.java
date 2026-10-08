package com.fitsync.gateway.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class GatewayConfig {

    private static final Duration COACH_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration COACH_READ_TIMEOUT = Duration.ofMinutes(2);

    private final GatewayCorsProperties corsProperties;

    public GatewayConfig(GatewayCorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    /** Every non-coach route answers in milliseconds, so a downstream that is slower than this is treated as down. */
    @Bean
    RestTemplate gatewayRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * The request factory the coach routes use.
     *
     * <p>Its own, because the five-second read timeout on the shared template is a deliberate
     * protection for routes that answer in milliseconds, and raising it everywhere to accommodate
     * one slow route would remove that protection from all of them. A coach turn waits on a
     * language model, and two minutes matches the service's own stream timeout.
     *
     * <p>Deliberately not wrapped in a buffering factory: the response body has to reach the client
     * as it is written, which is the whole point of the progress stream.
     */
    @Bean
    ClientHttpRequestFactory coachRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) COACH_CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) COACH_READ_TIMEOUT.toMillis());
        return factory;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Correlation-Id"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
