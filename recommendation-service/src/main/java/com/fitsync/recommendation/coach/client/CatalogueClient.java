package com.fitsync.recommendation.coach.client;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Reads the exercise and food catalogues from the services that own them.
 *
 * <p>Those catalogues belong to workout-service and nutrition-service, not here. Reading their
 * schemas directly would be faster to write and would quietly demolish the bounded-context argument
 * the architecture rests on, so the coach calls their APIs like any other client. The extra hop
 * costs tens of milliseconds against a model call measured in seconds.
 *
 * <p>The caller's own bearer token is forwarded unchanged, so the coach sees exactly what the user
 * would see. That is not only correct; it is necessary, because both catalogues carry per-owner
 * rows and a user's custom exercises and foods should be visible to their coach.
 *
 * <p>Failures are returned as a message rather than thrown. A catalogue being briefly unreachable
 * should give the model an observation it can work around, not abort the user's turn.
 */
public abstract class CatalogueClient {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogueClient.class);

    private final RestClient restClient;
    private final String baseUrl;
    private final String label;

    /**
     * @param builder already carries the coach's timeouts, which are transport configuration and
     *                belong to {@link com.fitsync.recommendation.coach.config.CoachHttpClientConfig}
     *                rather than to a client that only knows about catalogues
     */
    protected CatalogueClient(RestClient.Builder builder, String baseUrl, String label) {
        this.restClient = builder.build();
        this.baseUrl = baseUrl;
        this.label = label;
    }

    /**
     * @param path        the path under the service root, for example {@code /api/v1/workouts/exercises}
     * @param queryParams null values are dropped, so a caller can pass an optional filter through
     *                    without branching on it
     */
    protected String get(String path, Map<String, Object> queryParams, String bearerToken) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(baseUrl + path);
        for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
            if (entry.getValue() != null) {
                uri.queryParam(entry.getKey(), entry.getValue());
            }
        }
        // Built and encoded here, then handed over as a URI. Passing a string would send it
        // through the client's URI-template factory, which encodes it a second time: a space in a
        // query becomes %2520, the catalogue matches nothing, and the tool reports an honest zero
        // for a search that was never performed.
        URI url = uri.build().encode().toUri();
        try {
            return restClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException ex) {
            // The URL is logged, the token never is.
            LOG.warn("{} catalogue read failed for {}: {}", label, path, ex.getMessage());
            return null;
        }
    }

    protected static Map<String, Object> params() {
        return new LinkedHashMap<>();
    }
}
