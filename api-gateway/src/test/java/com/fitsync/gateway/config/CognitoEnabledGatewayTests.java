package com.fitsync.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import com.fitsync.gateway.service.GatewayProxyService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The gateway with Cognito turned on. The JWK set URI points at a host that does not exist: the decoder only
 * fetches keys when a Cognito token arrives, so the context must start without any network.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "security.jwt.secret=change-me-change-me-change-me-change-me",
        "security.cognito.enabled=true",
        "security.cognito.issuer-uri=https://cognito-idp.example.invalid/pool",
        "security.cognito.jwk-set-uri=https://cognito-idp.example.invalid/pool/.well-known/jwks.json",
        "security.cognito.audience=test-client",
        "security.cognito.principal-claim=username",
        "fitsync.gateway.routes.auth-service-base-url=http://auth-service:8080",
        "fitsync.gateway.routes.user-service-base-url=http://user-service:8081",
        "fitsync.gateway.routes.workout-service-base-url=http://workout-service:8082",
        "fitsync.gateway.routes.nutrition-service-base-url=http://nutrition-service:8083",
        "fitsync.gateway.routes.analytics-service-base-url=http://analytics-service:8085",
        "fitsync.gateway.routes.recommendation-service-base-url=http://recommendation-service:8086",
        "fitsync.gateway.routes.notification-service-base-url=http://notification-service:8087"
})
class CognitoEnabledGatewayTests {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GatewayProxyService gatewayProxyService;

    @Test
    void startsWithTheCognitoDecoderAndConverter() throws Exception {
        assertThat(context.getBeansOfType(JwtDecoder.class)).hasSize(1);
        assertThat(context.getBean("cognitoJwtAuthenticationConverter")).isInstanceOf(Converter.class);

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protectedRouteWithoutATokenIsStillUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void applicationTokenStillReachesAProtectedRoute() throws Exception {
        when(gatewayProxyService.forwardToUserService(any(), any()))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"proxied\"}".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/v1/users/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + applicationToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("proxied"));
    }

    private static String applicationToken() {
        Instant now = Instant.now();
        return Jwts.builder()
                .claims(Map.of(
                        "userId", UUID.randomUUID().toString(),
                        "displayName", "Test User",
                        "role", "USER"))
                .subject("test@example.com")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(
                        "change-me-change-me-change-me-change-me".getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
