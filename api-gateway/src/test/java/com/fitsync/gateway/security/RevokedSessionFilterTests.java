package com.fitsync.gateway.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fitsync.gateway.service.GatewayProxyService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The gateway's revoked-session check: a valid token of a revoked session is refused with SESSION_REVOKED, a token
 * issued before sessions existed is accepted, and Redis being down lets requests through. Redis is a mock.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "security.jwt.secret=change-me-change-me-change-me-change-me",
        "fitsync.gateway.routes.auth-service-base-url=http://auth-service:8080",
        "fitsync.gateway.routes.user-service-base-url=http://user-service:8081",
        "fitsync.gateway.routes.workout-service-base-url=http://workout-service:8082",
        "fitsync.gateway.routes.nutrition-service-base-url=http://nutrition-service:8083",
        "fitsync.gateway.routes.analytics-service-base-url=http://analytics-service:8085",
        "fitsync.gateway.routes.recommendation-service-base-url=http://recommendation-service:8086",
        "fitsync.gateway.routes.notification-service-base-url=http://notification-service:8087",
        "fitsync.gateway.cors.allowed-origins=http://localhost:4200"
})
class RevokedSessionFilterTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RevokedSessionFilter revokedSessionFilter;

    @MockBean
    private GatewayProxyService gatewayProxyService;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @Value("${security.jwt.secret}")
    private String signingSecret;

    @BeforeEach
    void setUp() {
        reset(redisTemplate);
        revokedSessionFilter.resetRedisBackoff();
        when(gatewayProxyService.forwardToUserService(any(), any()))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"proxied\"}".getBytes(StandardCharsets.UTF_8)));
        when(gatewayProxyService.forwardToAuthService(any(), any()))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"auth-proxied\"}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void aTokenOfARevokedSessionIsRefused() throws Exception {
        UUID sid = UUID.randomUUID();
        when(redisTemplate.hasKey("fitsync:revoked-sid:" + sid)).thenReturn(true);

        mockMvc.perform(get("/api/v1/users/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(sid))
                        .header("X-Correlation-Id", "corr-revoked"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/v1/users/me/profile"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.correlationId").value("corr-revoked"));

        verifyNoInteractions(gatewayProxyService);
    }

    @Test
    void aRevokedSessionCannotUseTheAuthRoutesEither() throws Exception {
        UUID sid = UUID.randomUUID();
        when(redisTemplate.hasKey("fitsync:revoked-sid:" + sid)).thenReturn(true);

        mockMvc.perform(delete("/api/v1/auth/sessions").param("scope", "others")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(sid)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));

        verifyNoInteractions(gatewayProxyService);
    }

    @Test
    void aTokenOfALiveSessionIsForwarded() throws Exception {
        UUID sid = UUID.randomUUID();
        when(redisTemplate.hasKey("fitsync:revoked-sid:" + sid)).thenReturn(false);

        mockMvc.perform(get("/api/v1/users/me/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(sid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("proxied"));

        verify(redisTemplate).hasKey("fitsync:revoked-sid:" + sid);
    }

    @Test
    void aTokenWithoutASessionIdIsAcceptedWithoutAskingRedis() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("proxied"));

        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    void whenRedisIsDownTheRequestIsLetThroughAndRedisIsLeftAloneForAWhile() throws Exception {
        when(redisTemplate.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("Connection refused"));

        mockMvc.perform(get("/api/v1/users/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("proxied"));
        mockMvc.perform(get("/api/v1/users/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(UUID.randomUUID())))
                .andExpect(status().isOk());

        // The second request fell inside the back-off, so Redis was asked only once.
        verify(redisTemplate, times(1)).hasKey(anyString());
    }

    @Test
    void refreshIsPublicAndForwarded() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"abc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("auth-proxied"));
    }

    @Test
    void refreshIsForwardedEvenWithAnExpiredAccessToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"abc\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void theSessionRoutesNeedAnApplicationToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/auth/sessions"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/auth/sessions/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/auth/sessions").param("scope", "others"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/sessions").header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(gatewayProxyService);
    }

    @Test
    void theRevokedCountHeaderIsReadableCrossOrigin() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/profile")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(null)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        org.hamcrest.Matchers.containsString("X-Revoked-Count")));
    }

    private String token(UUID sid) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", UUID.randomUUID().toString());
        claims.put("displayName", "Test User");
        claims.put("role", "USER");
        if (sid != null) {
            claims.put("sid", sid.toString());
        }
        Instant now = Instant.now();
        return Jwts.builder()
                .claims(claims)
                .subject("test@example.com")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(Keys.hmacShaKeyFor(signingSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private String expiredToken() {
        Instant past = Instant.now().minusSeconds(3600);
        return Jwts.builder()
                .claims(Map.of("userId", UUID.randomUUID().toString(), "role", "USER",
                        "sid", UUID.randomUUID().toString()))
                .subject("test@example.com")
                .issuedAt(Date.from(past))
                .expiration(Date.from(past.plusSeconds(900)))
                .signWith(Keys.hmacShaKeyFor(signingSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
