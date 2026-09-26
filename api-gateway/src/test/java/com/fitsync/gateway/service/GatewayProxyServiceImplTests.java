package com.fitsync.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
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
                "http://recommendation-service:8086"));

        server.expect(once(), requestTo("http://auth-service:8080/api/v1/auth/register?source=e2e"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        request.setQueryString("source=e2e");

        service.forwardToAuthService(request, "{}".getBytes(StandardCharsets.UTF_8));

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
                "http://recommendation-service:8086"));

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
                "http://recommendation-service:8086"));

        server.expect(once(), requestTo("http://recommendation-service:8086/api/v1/recommendations"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        service.forwardToRecommendationService(
                new MockHttpServletRequest("GET", "/api/v1/recommendations"), null);

        server.verify();
    }

    private static GatewayProxyService newServiceWithAuthBaseUrl(String authServiceBaseUrl) {
        return new GatewayProxyServiceImpl(new RestTemplate(), coachRequestFactory(), new GatewayRoutesProperties(
                authServiceBaseUrl,
                "http://user-service:8081",
                "http://workout-service:8082",
                "http://nutrition-service:8083",
                "http://analytics-service:8085",
                "http://recommendation-service:8086"));
    }

    /** The coach routes use their own factory; these tests exercise the collected forward. */
    private static org.springframework.http.client.ClientHttpRequestFactory coachRequestFactory() {
        return new org.springframework.http.client.SimpleClientHttpRequestFactory();
    }
}
