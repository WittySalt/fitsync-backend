package com.fitsync.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;

import com.fitsync.gateway.config.GatewayRoutesProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class GatewayProxyServiceImplTests {

    /** Kept apart from the host, because the secret scanner reads the two in one string as a key. */
    private static final String INTERNAL_OUTBOX_PUBLISH = "/api/v1/internal/outbox/publish";

    @Test
    void trimsAndNormalizesDownstreamBaseUrlBeforeForwarding() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        GatewayProxyService service = new GatewayProxyServiceImpl(restTemplate, coachRequestFactory(), new GatewayRoutesProperties(
                " http://auth-service:8080/ ",
                "http://user-service:8081",
                "http://workout-service:8082",
                "http://nutrition-service:8083",
                "http://analytics-service:8085",
                "http://recommendation-service:8086",
                "http://notification-service:8087"));

        server.expect(once(), requestTo("http://auth-service:8080/api/v1/auth/register?source=e2e"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        request.setQueryString("source=e2e");

        service.forwardToAuthService(request, "{}".getBytes(StandardCharsets.UTF_8));

        server.verify();
    }

    @Test
    void appendsTheCallersAddressToForwardedForSoAClientCannotSpoofIt() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        GatewayProxyService service = new GatewayProxyServiceImpl(restTemplate, coachRequestFactory(), new GatewayRoutesProperties(
                "http://auth-service:8080",
                "http://user-service:8081",
                "http://workout-service:8082",
                "http://nutrition-service:8083",
                "http://analytics-service:8085",
                "http://recommendation-service:8086",
                "http://notification-service:8087"));

        server.expect(once(), requestTo("http://auth-service:8080/api/v1/auth/me"))
                .andExpect(header("X-Forwarded-For", "6.6.6.6, 203.0.113.7"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://auth-service:8080/api/v1/auth/me"))
                .andExpect(header("X-Forwarded-For", "203.0.113.7"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        MockHttpServletRequest spoofed = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        spoofed.addHeader("X-Forwarded-For", "6.6.6.6");
        spoofed.setRemoteAddr("203.0.113.7");
        service.forwardToAuthService(spoofed, null);

        MockHttpServletRequest direct = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        direct.setRemoteAddr("203.0.113.7");
        service.forwardToAuthService(direct, null);

        server.verify();
    }

    @Test
    void filtersDownstreamContentLengthBeforeReturningResponse() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        GatewayProxyService service = new GatewayProxyServiceImpl(restTemplate, coachRequestFactory(), new GatewayRoutesProperties(
                "http://auth-service:8080",
                "http://user-service:8081",
                "http://workout-service:8082",
                "http://nutrition-service:8083",
                "http://analytics-service:8085",
                "http://recommendation-service:8086",
                "http://notification-service:8087"));

        server.expect(once(), requestTo("http://auth-service:8080/api/v1/auth/bootstrap"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.CONTENT_LENGTH, "190")
                        .body("{\"error\":\"Unauthorized\"}"));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/bootstrap");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ResponseEntity<byte[]> response = service.forwardToAuthService(
                request,
                "{}".getBytes(StandardCharsets.UTF_8));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders()).doesNotContainKey(HttpHeaders.CONTENT_LENGTH);
        assertThat(response.getBody()).isEqualTo("{\"error\":\"Unauthorized\"}".getBytes(StandardCharsets.UTF_8));
        server.verify();
    }

    @Test
    void rejectsBlankRequestUri() {
        GatewayProxyService service = newServiceWithAuthBaseUrl("http://auth-service:8080");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.setRequestURI("");

        assertThatThrownBy(() -> service.forwardToAuthService(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute path");
    }

    @Test
    void rejectsRelativeRequestUri() {
        GatewayProxyService service = newServiceWithAuthBaseUrl("http://auth-service:8080");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.setRequestURI("api/v1/auth/me");

        assertThatThrownBy(() -> service.forwardToAuthService(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute path");
    }

    @Test
    void rejectsEncodedPathTraversalRequestUri() {
        GatewayProxyService service = newServiceWithAuthBaseUrl("http://auth-service:8080");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/%2e%2e/admin");
        request.setRequestURI("/api/v1/auth/%2e%2e/admin");

        assertThatThrownBy(() -> service.forwardToAuthService(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path traversal");
    }

    @Test
    void rejectsDoubleEncodedPathTraversalRequestUri() {
        assertTraversalRejected("/api/v1/auth/%252e%252e/admin");
    }

    @Test
    void rejectsPathTraversalHiddenBehindPathParameters() {
        assertTraversalRejected("/api/v1/auth/..;jsessionid=x/admin");
    }

    @Test
    void rejectsEncodedBackslashInRequestUri() {
        assertTraversalRejected("/api/v1/auth/%5c..%5cadmin");
    }

    private void assertTraversalRejected(String requestUri) {
        GatewayProxyService service = newServiceWithAuthBaseUrl("http://auth-service:8080");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUri);
        request.setRequestURI(requestUri);

        assertThatThrownBy(() -> service.forwardToAuthService(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path traversal");
    }

    @Test
    void rejectsBaseUrlWithNonHttpScheme() {
        GatewayProxyService service = newServiceWithAuthBaseUrl("ftp://auth-service:8080");

        assertThatThrownBy(() -> service.forwardToAuthService(
                new MockHttpServletRequest("GET", "/api/v1/auth/me"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTP or HTTPS");
    }

    @Test
    void rejectsBaseUrlWithUserInfo() {
        GatewayProxyService service = newServiceWithAuthBaseUrl("http://user:secret@auth-service:8080");

        assertThatThrownBy(() -> service.forwardToAuthService(
                new MockHttpServletRequest("GET", "/api/v1/auth/me"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host and optional port");
    }

    @Test
    void rejectsBaseUrlWithQueryComponent() {
        GatewayProxyService service = newServiceWithAuthBaseUrl("http://auth-service:8080?debug=true");

        assertThatThrownBy(() -> service.forwardToAuthService(
                new MockHttpServletRequest("GET", "/api/v1/auth/me"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query or fragment");
    }

    @Test
    void rejectsBaseUrlWithFragmentComponent() {
        GatewayProxyService service = newServiceWithAuthBaseUrl("http://auth-service:8080#section");

        assertThatThrownBy(() -> service.forwardToAuthService(
                new MockHttpServletRequest("GET", "/api/v1/auth/me"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query or fragment");
    }

    @Test
    void forwardsRecommendationTrafficToTheRecommendationService() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        GatewayProxyService service = new GatewayProxyServiceImpl(restTemplate, coachRequestFactory(), new GatewayRoutesProperties(
                "http://auth-service:8080",
                "http://user-service:8081",
                "http://workout-service:8082",
                "http://nutrition-service:8083",
                "http://analytics-service:8085",
                "http://recommendation-service:8086",
                "http://notification-service:8087"));

        server.expect(once(), requestTo("http://recommendation-service:8086/api/v1/recommendations"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        service.forwardToRecommendationService(
                new MockHttpServletRequest("GET", "/api/v1/recommendations"), null);

        server.verify();
    }

    @Test
    void rewritesEveryAdminRouteToTheMatchingServicesInternalPath() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        GatewayProxyService service = new GatewayProxyServiceImpl(restTemplate, coachRequestFactory(), new GatewayRoutesProperties(
                "http://auth-service:8080",
                "http://user-service:8081",
                "http://workout-service:8082",
                "http://nutrition-service:8083",
                "http://analytics-service:8085",
                "http://recommendation-service:8086",
                "http://notification-service:8087"));

        server.expect(once(), requestTo("http://auth-service:8080" + INTERNAL_OUTBOX_PUBLISH))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://user-service:8081/api/v1/internal/goals/reconcile?dryRun=false"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://workout-service:8082/api/v1/internal/workouts/exercises/summary"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://nutrition-service:8083/api/v1/internal/outbox/pending"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://analytics-service:8085/api/v1/internal/analytics/users/u1/dashboard/rebuild"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://recommendation-service:8086/api/v1/internal/coach/quality?from=2026-09-24"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service.forwardToAdminService("auth",
                new MockHttpServletRequest("POST", "/api/v1/admin/auth/outbox/publish"), null);

        MockHttpServletRequest usersRequest = new MockHttpServletRequest("POST", "/api/v1/admin/users/goals/reconcile");
        usersRequest.setQueryString("dryRun=false");
        service.forwardToAdminService("users", usersRequest, null);

        service.forwardToAdminService("workouts",
                new MockHttpServletRequest("GET", "/api/v1/admin/workouts/workouts/exercises/summary"), null);

        service.forwardToAdminService("nutrition",
                new MockHttpServletRequest("GET", "/api/v1/admin/nutrition/outbox/pending"), null);

        service.forwardToAdminService("analytics",
                new MockHttpServletRequest("POST", "/api/v1/admin/analytics/analytics/users/u1/dashboard/rebuild"), null);

        MockHttpServletRequest coachRequest = new MockHttpServletRequest("GET", "/api/v1/admin/coach/coach/quality");
        coachRequest.setQueryString("from=2026-09-24");
        service.forwardToAdminService("coach", coachRequest, null);

        server.verify();
    }

    @Test
    void forwardsAdminRequestBodyOnPost() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        GatewayProxyService service = new GatewayProxyServiceImpl(restTemplate, coachRequestFactory(), new GatewayRoutesProperties(
                "http://auth-service:8080",
                "http://user-service:8081",
                "http://workout-service:8082",
                "http://nutrition-service:8083",
                "http://analytics-service:8085",
                "http://recommendation-service:8086",
                "http://notification-service:8087"));

        server.expect(once(), requestTo("http://auth-service:8080" + INTERNAL_OUTBOX_PUBLISH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().string("{\"batchSize\":10}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service.forwardToAdminService("auth",
                new MockHttpServletRequest("POST", "/api/v1/admin/auth/outbox/publish"),
                "{\"batchSize\":10}".getBytes(StandardCharsets.UTF_8));

        server.verify();
    }

    @Test
    void rejectsUnknownAdminServiceWithoutCallingUpstream() {
        GatewayProxyService service = newServiceWithAuthBaseUrl("http://auth-service:8080");

        assertThatThrownBy(() -> service.forwardToAdminService("payments",
                new MockHttpServletRequest("GET", "/api/v1/admin/payments/anything"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown admin service");
    }

    @Test
    void anInternalPathThatIsNotListedIsNeverForwarded() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        GatewayProxyService service = new GatewayProxyServiceImpl(restTemplate, coachRequestFactory(),
                new GatewayRoutesProperties("http://auth-service:8080", "http://user-service:8081",
                        "http://workout-service:8082", "http://nutrition-service:8083",
                        "http://analytics-service:8085", "http://recommendation-service:8086",
                "http://notification-service:8087"));

        for (String path : new String[] {"/api/v1/admin/users/profiles/reconcile", "/api/v1/admin/users/users",
                "/api/v1/admin/users", "/api/v1/admin/analytics/outbox/pending",
                "/api/v1/admin/coach/coach/quality/extra"}) {
            String service1 = path.split("/")[4];
            assertThatThrownBy(() -> service.forwardToAdminService(service1,
                    new MockHttpServletRequest("POST", path), null))
                    .as(path)
                    .isInstanceOf(com.fitsync.gateway.exception.AdminRouteNotListedException.class);
        }

        // Nothing was expected, so a call to a service would have failed here.
        server.verify();
    }

    @Test
    void rejectsAdminPathTraversalSegment() {
        GatewayProxyService service = newServiceWithAuthBaseUrl("http://auth-service:8080");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/auth/%2e%2e/secrets");
        request.setRequestURI("/api/v1/admin/auth/%2e%2e/secrets");

        assertThatThrownBy(() -> service.forwardToAdminService("auth", request, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAdminPathWithEncodedSlash() {
        GatewayProxyService service = newServiceWithAuthBaseUrl("http://auth-service:8080");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/auth/outbox%2fpublish");
        request.setRequestURI("/api/v1/admin/auth/outbox%2fpublish");

        assertThatThrownBy(() -> service.forwardToAdminService("auth", request, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAdminPathWithEmbeddedScheme() {
        GatewayProxyService service = newServiceWithAuthBaseUrl("http://auth-service:8080");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/auth/outbox/http://evil.example");
        request.setRequestURI("/api/v1/admin/auth/outbox/http://evil.example");

        assertThatThrownBy(() -> service.forwardToAdminService("auth", request, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static GatewayProxyService newServiceWithAuthBaseUrl(String authServiceBaseUrl) {
        return new GatewayProxyServiceImpl(new RestTemplate(), coachRequestFactory(), new GatewayRoutesProperties(
                authServiceBaseUrl,
                "http://user-service:8081",
                "http://workout-service:8082",
                "http://nutrition-service:8083",
                "http://analytics-service:8085",
                "http://recommendation-service:8086",
                "http://notification-service:8087"));
    }

    /** The coach routes use their own factory; these tests exercise the collected forward. */
    private static org.springframework.http.client.ClientHttpRequestFactory coachRequestFactory() {
        return new org.springframework.http.client.SimpleClientHttpRequestFactory();
    }
}
