package com.fitsync.gateway.config;

import com.fitsync.gateway.service.GatewayProxyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The prod gateway runs on the default allowlist (the prod deploy does not set
 * FITSYNC_CORS_ALLOWED_ORIGINS), so these tests leave the property unset. The prod
 * CloudFront distribution serves the SPA on its own name and on the apex and www
 * custom domains, and forwards the browser's Origin to the gateway, so all three
 * must pass the CORS check or every POST from the custom domain is rejected.
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
        "fitsync.gateway.routes.notification-service-base-url=http://notification-service:8087"
})
class GatewayCorsDefaultsTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GatewayProxyService gatewayProxyService;

    @ParameterizedTest
    @ValueSource(strings = {
            "https://d26vi6hikexzev.cloudfront.net",
            "https://fitsyncapp.click",
            "https://www.fitsyncapp.click"
    })
    void defaultAllowlistAcceptsEveryProdOrigin(String origin) throws Exception {
        mockMvc.perform(options("/api/v1/auth/bootstrap")
                        .header(HttpHeaders.ORIGIN, origin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://fitsyncapp.click",
            "https://fitsyncapp.click.evil.test",
            "https://evil.fitsyncapp.click"
    })
    void defaultAllowlistRejectsLookAlikeOrigins(String origin) throws Exception {
        mockMvc.perform(options("/api/v1/auth/bootstrap")
                        .header(HttpHeaders.ORIGIN, origin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void propertiesFallBackToTheProdOriginsWhenUnset() {
        assertThat(new GatewayCorsProperties(null).allowedOrigins())
                .contains("https://d26vi6hikexzev.cloudfront.net",
                        "https://fitsyncapp.click",
                        "https://www.fitsyncapp.click");
    }
}
