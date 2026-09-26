package com.fitsync.recommendation.coach.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * How the catalogue clients build a request.
 *
 * <p>Worth testing on its own because the failure it pins was invisible from the outside. A query
 * containing a space was encoded twice, so {@code chicken breast} reached nutrition-service as
 * {@code chicken%2520breast}, matched nothing, and the tool truthfully reported no candidates for a
 * search that had never really run. Nothing failed, nothing was logged, and the model went on to
 * invent the foods it could not find.
 */
class CatalogueClientTests {

    /** The concrete clients add paths; the encoding and header behaviour lives in the base. */
    private static final class TestClient extends CatalogueClient {
        private TestClient(RestClient.Builder builder) {
            super(builder, "http://nutrition-service:8083", "nutrition");
        }

        private String search(String query) {
            Map<String, Object> params = params();
            params.put("q", query);
            params.put("size", 20);
            params.put("scope", null);
            return get("/api/v1/nutrition/foods/search", params, "the-token");
        }
    }

    @Test
    @DisplayName("a query with a space is encoded exactly once")
    void encodesASpaceOnce() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "http://nutrition-service:8083/api/v1/nutrition/foods/search?q=chicken%20breast&size=20"))
                .andRespond(withSuccess("{\"content\":[]}", MediaType.APPLICATION_JSON));

        String body = new TestClient(builder).search("chicken breast");

        server.verify();
        assertThat(body).isEqualTo("{\"content\":[]}");
    }

    @Test
    @DisplayName("a null filter is dropped rather than sent as the text \"null\"")
    void dropsNullParameters() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(
                        "http://nutrition-service:8083/api/v1/nutrition/foods/search?q=oats&size=20"))
                .andRespond(withSuccess("{\"content\":[]}", MediaType.APPLICATION_JSON));

        new TestClient(builder).search("oats");

        server.verify();
    }

    @Test
    @DisplayName("the caller's own token is forwarded, so the coach sees what the user sees")
    void forwardsTheBearerToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(header(HttpHeaders.AUTHORIZATION, "Bearer the-token"))
                .andRespond(withSuccess("{\"content\":[]}", MediaType.APPLICATION_JSON));

        new TestClient(builder).search("oats");

        server.verify();
    }

    @Test
    @DisplayName("a failing catalogue comes back as null rather than throwing into the turn")
    void returnsNullOnFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/foods/search")))
                .andRespond(withServerError());

        assertThat(new TestClient(builder).search("oats")).isNull();
    }
}
