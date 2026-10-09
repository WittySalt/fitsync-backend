package com.fitsync.gateway;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

/**
 * The admin route through the real proxy service, with the downstream call replaced by a server that expects
 * exactly the calls a test lists: a listed internal path is forwarded, and an internal path that is not on the
 * list is a 404 that never reaches the service.
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
        "fitsync.gateway.cors.allowed-origins=http://localhost:3000"
})
class AdminInternalPathListTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestTemplate gatewayRestTemplate;

    private MockRestServiceServer downstream;

    @BeforeEach
    void bindDownstream() {
        downstream = MockRestServiceServer.bindTo(gatewayRestTemplate).build();
    }

    @Test
    void aListedInternalPathIsForwardedToItsService() throws Exception {
        downstream.expect(once(), requestTo("http://user-service:8081/api/v1/internal/goals/reconcile"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"publishedCount\":2}", MediaType.APPLICATION_JSON));
        downstream.expect(once(),
                        requestTo("http://analytics-service:8085/api/v1/internal/analytics/users/u-1/dashboard/rebuild"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/admin/users/goals/reconcile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + signedToken("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedCount").value(2));
        mockMvc.perform(post("/api/v1/admin/analytics/analytics/users/u-1/dashboard/rebuild")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + signedToken("ADMIN")))
                .andExpect(status().isOk());

        downstream.verify();
    }

    @Test
    void theEmailSendNowRouteIsForwardedToNotificationService() throws Exception {
        downstream.expect(once(),
                        requestTo("http://notification-service:8087/api/v1/internal/users/u-1/emails"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"kinds\":[]}", MediaType.APPLICATION_JSON));
        downstream.expect(once(),
                        requestTo("http://notification-service:8087/api/v1/internal/users/u-1/emails"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"kind\":\"GOAL_REACHED\",\"outcome\":\"SENT\"}",
                        MediaType.APPLICATION_JSON));

        String admin = "Bearer " + signedToken("ADMIN");
        mockMvc.perform(get("/api/v1/admin/notifications/users/u-1/emails").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/admin/notifications/users/u-1/emails").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"GOAL_REACHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("SENT"));
        mockMvc.perform(get("/api/v1/admin/notifications/users/u-1/emails")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + signedToken("USER")))
                .andExpect(status().isForbidden());

        downstream.verify();
    }

    @Test
    void anInternalPathThatIsNotListedIsNotFoundAndNeverReachesTheService() throws Exception {
        String admin = "Bearer " + signedToken("ADMIN");

        // Real internal paths of the services that the console does not use, and ones that do not exist at all.
        mockMvc.perform(post("/api/v1/admin/users/profiles/reconcile").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/v1/admin/users/anything").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/admin/analytics/outbox/pending").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/admin/coach").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/admin/nutrition/outbox/pending/").header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());
        // A listed pattern only matches one segment where it has a star.
        mockMvc.perform(post("/api/v1/admin/analytics/analytics/users/a/b/dashboard/rebuild")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isNotFound());

        // The server expected nothing, so any call to a service would have failed this.
        downstream.verify();
    }

    @Test
    void theListIsStillBehindTheAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/goals/reconcile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + signedToken("USER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/users/goals/reconcile")).andExpect(status().isUnauthorized());
        downstream.verify();
    }

    private static String signedToken(String role) {
        Instant now = Instant.now();
        SecretKey key = Keys.hmacShaKeyFor("change-me-change-me-change-me-change-me".getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claims(Map.of("userId", UUID.randomUUID().toString(), "displayName", "Test User", "role", role))
                .subject("test@example.com")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(key)
                .compact();
    }
}
