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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "security.jwt.secret=change-me-change-me-change-me-change-me",
        "fitsync.gateway.routes.auth-service-base-url=http://auth-service:8080",
        "fitsync.gateway.routes.user-service-base-url=http://user-service:8081"
})
class ApiGatewayApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GatewayProxyService gatewayProxyService;

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

    private String validToken() {
        Instant now = Instant.now();
        SecretKey secretKey = Keys.hmacShaKeyFor("change-me-change-me-change-me-change-me".getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claims(Map.of(
                        "userId", UUID.randomUUID().toString(),
                        "displayName", "Test User",
                        "role", "USER"))
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
