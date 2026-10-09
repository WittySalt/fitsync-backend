package com.fitsync.gateway;

import com.fitsync.gateway.service.GatewayProxyService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        "fitsync.gateway.cors.allowed-origins=https://d26vi6hikexzev.cloudfront.net,http://localhost:3000"
})
class ApiGatewayApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GatewayProxyService gatewayProxyService;

    @Autowired
    private org.springframework.context.ApplicationContext context;

    @Test
    void startsWithoutCognitoBeansWhileCognitoIsDisabled() {
        assertThat(context.getBeansOfType(org.springframework.security.oauth2.jwt.JwtDecoder.class)).isEmpty();
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("api-gateway"));
    }

    @Test
    void loginRouteIsPublicAndProxied() throws Exception {
        when(gatewayProxyService.forwardToAuthService(any(), argThat(matchesBody("{}"))))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"token\":\"ok\"}".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("ok"));
    }

    @Test
    void corsPreflightAllowsConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/users/me/profile")
                        .header(HttpHeaders.ORIGIN, "https://d26vi6hikexzev.cloudfront.net")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "https://d26vi6hikexzev.cloudfront.net"));
    }

    @Test
    void corsPreflightRejectsUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/users/me/profile")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unsupportedTraceMethodIsRejectedBeforeProxying() throws Exception {
        mockMvc.perform(request(HttpMethod.TRACE, "/api/v1/users/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bootstrapRouteIsPublicAndProxiedWithBearerToken() throws Exception {
        when(gatewayProxyService.forwardToAuthService(any(), argThat(matchesBody("{}"))))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"accessToken\":\"local-token\"}".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer cognito-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("local-token"));
    }

    @Test
    void cancellingADeletionIsProxiedWithTheIdentityTokenItCarries() throws Exception {
        when(gatewayProxyService.forwardToAuthService(any(), any()))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"NOT_REQUESTED\"}".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(post("/api/v1/auth/account-deletion/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer cognito-identity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_REQUESTED"));
    }

    @Test
    void theOtherAccountDeletionRoutesStillNeedAnApplicationToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/account-deletion/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedUserRouteRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void protectedUserRouteRejectsMalformedToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void protectedUserRouteForwardsValidatedToken() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Correlation-Id", "corr-123");
        when(gatewayProxyService.forwardToUserService(any(), any()))
                .thenReturn(new ResponseEntity<>("{\"status\":\"proxied\"}".getBytes(StandardCharsets.UTF_8), headers, HttpStatus.OK));

        mockMvc.perform(get("/api/v1/users/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("proxied"))
                .andExpect(header().string("X-Correlation-Id", "corr-123"));

        verify(gatewayProxyService).forwardToUserService(any(), argThat(emptyBody()));
    }

    @Test
    void protectedWorkoutRouteRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/workouts/plans"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void protectedWorkoutRouteForwardsValidatedToken() throws Exception {
        when(gatewayProxyService.forwardToWorkoutService(any(), any()))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"workout-proxied\"}".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/v1/workouts/plans")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("workout-proxied"));

        verify(gatewayProxyService).forwardToWorkoutService(any(), argThat(emptyBody()));
    }

    @Test
    void protectedNutritionRouteRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/nutrition/meals"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void protectedNutritionRouteForwardsValidatedToken() throws Exception {
        when(gatewayProxyService.forwardToNutritionService(any(), any()))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"nutrition-proxied\"}".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/v1/nutrition/meals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("nutrition-proxied"));

        verify(gatewayProxyService).forwardToNutritionService(any(), argThat(emptyBody()));
    }

    @Test
    void protectedAnalyticsRouteForwardsValidatedToken() throws Exception {
        when(gatewayProxyService.forwardToAnalyticsService(any(), any()))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"analytics-proxied\"}".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/v1/analytics/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("analytics-proxied"));

        verify(gatewayProxyService).forwardToAnalyticsService(any(), argThat(emptyBody()));
    }

    @Test
    void protectedRecommendationRouteRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/recommendations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void protectedRecommendationRouteForwardsValidatedToken() throws Exception {
        when(gatewayProxyService.forwardToRecommendationService(any(), any()))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"recommendation-proxied\"}".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/v1/recommendations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("recommendation-proxied"));

        verify(gatewayProxyService).forwardToRecommendationService(any(), argThat(emptyBody()));
    }

    @Test
    void unknownAuthenticatedApiRouteReturnsNotFoundWithoutProxying() throws Exception {
        mockMvc.perform(get("/api/v1/unknown/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken())
                        .header("X-Correlation-Id", "corr-missing-route"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/unknown/dashboard"))
                .andExpect(jsonPath("$.correlationId").value("corr-missing-route"));

        verifyNoInteractions(gatewayProxyService);
    }

    @Test
    void adminRouteRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/auth/outbox/pending"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(gatewayProxyService);
    }

    @Test
    void adminRouteRejectsOrdinaryUserRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/auth/outbox/pending")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(gatewayProxyService);
    }

    @Test
    void adminRouteForwardsToAuthServiceForAdminCaller() throws Exception {
        when(gatewayProxyService.forwardToAdminService(eq("auth"), any(), any()))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"pending\":0,\"oldestCreatedAt\":null}".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/v1/admin/auth/outbox/pending")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(0));

        verify(gatewayProxyService).forwardToAdminService(eq("auth"), any(), argThat(emptyBody()));
    }

    @Test
    void adminRouteForwardsRequestBodyOnPost() throws Exception {
        when(gatewayProxyService.forwardToAdminService(eq("workouts"), any(), any()))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"queued\"}".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(post("/api/v1/admin/workouts/outbox/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchSize\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("queued"));

        verify(gatewayProxyService).forwardToAdminService(
                eq("workouts"), any(), argThat(matchesBody("{\"batchSize\":5}")));
    }

    @Test
    void adminRouteForwardsAuthorizationHeaderDownstream() throws Exception {
        String token = adminToken();
        when(gatewayProxyService.forwardToAdminService(eq("coach"), any(), any()))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/v1/admin/coach/coach/quality")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        ArgumentCaptor<HttpServletRequest> requestCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(gatewayProxyService).forwardToAdminService(eq("coach"), requestCaptor.capture(), any());
        assertThat(requestCaptor.getValue().getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + token);
    }

    @Test
    void adminRouteRejectsUnknownServiceWithoutProxying() throws Exception {
        mockMvc.perform(get("/api/v1/admin/payments/anything")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verifyNoInteractions(gatewayProxyService);
    }

    @Test
    void adminRouteRejectsPathTraversalAttempt() throws Exception {
        // Built from a java.net.URI, not the string-template overload, so the %2e%2e segment
        // reaches the servlet exactly as written instead of being decoded while the request is built.
        // Spring Security's own StrictHttpFirewall already rejects an encoded ".." before the
        // request reaches this controller, so only the 400 is asserted here; the controller's own
        // rewriteAdminPath check is exercised directly by the service-layer tests instead.
        mockMvc.perform(get(new java.net.URI("/api/v1/admin/auth/%2e%2e/secrets"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void internalRouteIsNotReachableThroughTheGateway() throws Exception {
        mockMvc.perform(get("/api/v1/internal/outbox/pending")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verifyNoInteractions(gatewayProxyService);
    }

    private String validToken() {
        return signedToken("USER");
    }

    private String adminToken() {
        return signedToken("ADMIN");
    }

    private String signedToken(String role) {
        Instant now = Instant.now();
        SecretKey secretKey = Keys.hmacShaKeyFor("change-me-change-me-change-me-change-me".getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claims(Map.of(
                        "userId", UUID.randomUUID().toString(),
                        "displayName", "Test User",
                        "role", role))
                .subject("test@example.com")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(secretKey)
                .compact();
    }

    private ArgumentMatcher<byte[]> emptyBody() {
        return body -> body == null || body.length == 0;
    }

    private ArgumentMatcher<byte[]> matchesBody(String expected) {
        return body -> expected.equals(new String(body, StandardCharsets.UTF_8));
    }
}
