package com.fitsync.recommendation.coach.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.config.CoachProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class UserProfileClientTests {

    private static final String GATEWAY = "http://api-gateway:8088";

    @Test
    void readsTheConsentGatedCoachContextNotTheFullQuestionnaire() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String body = "{\"healthDataConsent\":false,\"injuryAreas\":[]}";
        server.expect(requestTo(GATEWAY + "/api/v1/users/me/health-readiness/coach-context"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer caller-credential"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        String result = new UserProfileClient(builder, new CoachProperties(null, null, GATEWAY, null, null),
                new ObjectMapper()).coachHealthContext("caller-credential");

        server.verify();
        assertThat(result).isEqualTo(body);
    }
}
