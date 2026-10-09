package com.fitsync.gateway.ratelimit;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;

import com.fitsync.gateway.service.GatewayProxyService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "security.jwt.secret=change-me-change-me-change-me-change-me",
        "fitsync.gateway.rate-limit.enabled=true",
        "fitsync.gateway.rate-limit.general-limit=120",
        "fitsync.gateway.rate-limit.auth-limit=10",
        "fitsync.gateway.rate-limit.refresh-limit=60",
        "fitsync.gateway.rate-limit.trusted-proxy-hops=2",
        "fitsync.gateway.cors.allowed-origins=https://d26vi6hikexzev.cloudfront.net"
})
class RateLimitFilterTests {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String SECRET = "change-me-change-me-change-me-change-me";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RedisRateLimiter rateLimiter;

    @MockBean
    private GatewayProxyService gatewayProxyService;

    @Test
    void anAuthenticatedRequestIsCountedUnderItsUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        allow();
        when(gatewayProxyService.forwardToUserService(any(), any())).thenReturn(ResponseEntity.ok(new byte[0]));

        mockMvc.perform(get("/api/v1/users/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(userId))
                        .header("X-Forwarded-For", "203.0.113.7, 130.176.10.5"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "120"))
                .andExpect(header().string("X-RateLimit-Remaining", "119"));

        verify(rateLimiter).tryAcquire("fitsync:ratelimit:general:user:" + userId, 120, WINDOW);
    }

    @Test
    void anUnauthenticatedSignInIsCountedUnderTheClientAddressWithTheAuthLimit() throws Exception {
        allow();
        when(gatewayProxyService.forwardToAuthService(any(), any())).thenReturn(ResponseEntity.ok(new byte[0]));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(remoteAddress("10.0.1.20"))
                        .header("X-Forwarded-For", "6.6.6.6, 203.0.113.7, 130.176.10.5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(rateLimiter).tryAcquire("fitsync:ratelimit:auth:ip:203.0.113.7", 10, WINDOW);
    }

    @Test
    void anUnauthenticatedRefreshHasItsOwnBucketWithTheRefreshLimit() throws Exception {
        allow();
        when(gatewayProxyService.forwardToAuthService(any(), any())).thenReturn(ResponseEntity.ok(new byte[0]));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(remoteAddress("10.0.1.20"))
                        .header("X-Forwarded-For", "6.6.6.6, 203.0.113.7, 130.176.10.5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                // The route is opened up for anonymous callers by the sessions PR (#85); the count comes first
                // either way, because this filter runs before authorization.
                .andExpect(header().string("X-RateLimit-Limit", "60"));

        verify(rateLimiter).tryAcquire("fitsync:ratelimit:refresh:ip:203.0.113.7", 60, WINDOW);
        verify(rateLimiter, never()).tryAcquire(eq("fitsync:ratelimit:auth:ip:203.0.113.7"), anyInt(), any());
    }

    @Test
    void refreshesNeverUseUpTheSignInBudgetOfTheSameAddress() throws Exception {
        allow();
        when(gatewayProxyService.forwardToAuthService(any(), any())).thenReturn(ResponseEntity.ok(new byte[0]));

        mockMvc.perform(post("/api/v1/auth/refresh").with(remoteAddress("192.0.2.10"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
        mockMvc.perform(post("/api/v1/auth/bootstrap").with(remoteAddress("192.0.2.10"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
        mockMvc.perform(post("/api/v1/auth/login").with(remoteAddress("192.0.2.10"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"));

        verify(rateLimiter).tryAcquire("fitsync:ratelimit:refresh:ip:192.0.2.10", 60, WINDOW);
        // Sign-in and bootstrap still share the stricter counter.
        verify(rateLimiter, times(2)).tryAcquire("fitsync:ratelimit:auth:ip:192.0.2.10", 10, WINDOW);
    }

    @Test
    void anEncodedRefreshPathIsStillTheRefreshBucket() throws Exception {
        allow();
        when(gatewayProxyService.forwardToAuthService(any(), any())).thenReturn(ResponseEntity.ok(new byte[0]));

        mockMvc.perform(post(URI.create("/api/v1/auth/%72efresh"))
                .with(remoteAddress("192.0.2.10"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        verify(rateLimiter).tryAcquire("fitsync:ratelimit:refresh:ip:192.0.2.10", 60, WINDOW);
    }

    @Test
    void aRefreshOverItsLimitAnswers429() throws Exception {
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(RateLimitDecision.denied(60, 7));

        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "7"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        verifyNoInteractions(gatewayProxyService);
    }

    @Test
    void anEncodedAuthPathIsStillCountedUnderTheAuthLimit() throws Exception {
        allow();
        when(gatewayProxyService.forwardToAuthService(any(), any())).thenReturn(ResponseEntity.ok(new byte[0]));

        mockMvc.perform(post(URI.create("/api/v1/%61uth/login"))
                .with(remoteAddress("192.0.2.10"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        verify(rateLimiter).tryAcquire("fitsync:ratelimit:auth:ip:192.0.2.10", 10, WINDOW);
    }

    @Test
    void anUnauthenticatedRequestElsewhereIsCountedUnderTheGeneralLimit() throws Exception {
        allow();

        mockMvc.perform(get("/api/v1/users/me/profile").with(remoteAddress("192.0.2.10")))
                .andExpect(status().isUnauthorized());

        verify(rateLimiter).tryAcquire("fitsync:ratelimit:general:ip:192.0.2.10", 120, WINDOW);
    }

    @Test
    void overTheLimitAnswers429WithRetryAfterAndTheStandardErrorBody() throws Exception {
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any()))
                .thenReturn(RateLimitDecision.denied(10, 42));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Correlation-Id", "corr-429")
                        .header(HttpHeaders.ORIGIN, "https://d26vi6hikexzev.cloudfront.net")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "42"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "https://d26vi6hikexzev.cloudfront.net"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.message").value("Too many requests. Try again in 42 seconds."))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"))
                .andExpect(jsonPath("$.correlationId").value("corr-429"));

        verifyNoInteractions(gatewayProxyService);
    }

    @Test
    void whenRedisCannotBeAskedTheRequestGoesThroughWithoutLimitHeaders() throws Exception {
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(RateLimitDecision.unchecked(10));
        when(gatewayProxyService.forwardToAuthService(any(), any())).thenReturn(ResponseEntity.ok(new byte[0]));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-RateLimit-Limit"))
                .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void theHealthCheckIsNeverCounted() throws Exception {
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(RateLimitDecision.denied(1, 60));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        // Sub-paths are not public (401 from the security rules), but they are not counted either.
        mockMvc.perform(get("/actuator/health/liveness"));

        verify(rateLimiter, never()).tryAcquire(anyString(), anyInt(), any());
    }

    @Test
    void aCorsPreflightIsNeverCounted() throws Exception {
        mockMvc.perform(options("/api/v1/users/me/profile")
                        .header(HttpHeaders.ORIGIN, "https://d26vi6hikexzev.cloudfront.net")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk());

        verify(rateLimiter, never()).tryAcquire(anyString(), anyInt(), any());
    }

    @Test
    void theCoachRoutesAreOnTheGeneralLimitOnly() throws Exception {
        UUID userId = UUID.randomUUID();
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(RateLimitDecision.denied(120, 5));

        mockMvc.perform(get("/api/v1/coach/brief").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(userId)))
                .andExpect(status().isTooManyRequests());

        verify(rateLimiter).tryAcquire(eq("fitsync:ratelimit:general:user:" + userId), eq(120), eq(WINDOW));
        verifyNoInteractions(gatewayProxyService);
    }

    private void allow() {
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any()))
                .thenAnswer(invocation -> RateLimitDecision.allowed(invocation.getArgument(1),
                        (int) invocation.getArgument(1) - 1L));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor remoteAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private static String token(UUID userId) {
        Instant now = Instant.now();
        SecretKey secretKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claims(Map.of("userId", userId.toString(), "displayName", "Test User", "role", "USER"))
                .subject("test@example.com")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(secretKey)
                .compact();
    }
}
