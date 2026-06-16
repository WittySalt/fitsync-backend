package com.fitsync.gateway.service;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;

import com.fitsync.gateway.config.GatewayRoutesProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class GatewayProxyServiceImplTests {

    @Test
    void trimsAndNormalizesDownstreamBaseUrlBeforeForwarding() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        GatewayProxyService service = new GatewayProxyServiceImpl(restTemplate, new GatewayRoutesProperties(
                " http://auth-service:8080/ ",
                "http://user-service:8081",
                "http://workout-service:8082",
                "http://nutrition-service:8083",
                "http://analytics-service:8085"));

        server.expect(once(), requestTo("http://auth-service:8080/api/v1/auth/register?source=e2e"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        request.setQueryString("source=e2e");

        service.forwardToAuthService(request, "{}".getBytes(StandardCharsets.UTF_8));

        server.verify();
    }
}
