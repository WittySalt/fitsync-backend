package com.fitsync.recommendation.coach.client;

import java.net.URI;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.fitsync.web.timezone.ClientZoneHeader;
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
        return get(path, queryParams, bearerToken, null);
    }

    /**
     * As {@link #get(String, Map, String)}, and forwards the client's zone as the X-Timezone header, the one
     * place a zone travels between services. A zone that is blank or not a usable zone id is not sent, so the
     * callee falls back as it does for any client that sent none.
     */
    protected String get(String path, Map<String, Object> queryParams, String bearerToken, String timezone) {
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
            RestClient.RequestHeadersSpec<?> request = restClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
            Optional<ZoneId> zone = ClientZoneHeader.parse(timezone);
            if (zone.isPresent()) {
                request = request.header(ClientZoneHeader.NAME, zone.get().getId());
            }
            return request.retrieve().body(String.class);
        } catch (RuntimeException ex) {
            // The URL is logged, the token never is.
            LOG.warn("{} catalogue read failed for {}: {}", label, path, ex.getMessage());
            return null;
        }
    }

    /**
     * Writes to a downstream service, and unlike {@link #get} does not hide failure.
     *
     * <p>A read that fails becomes an observation the model works around, which is the right shape
     * for a read. A write that fails is a different matter: the user asked for something to be
     * created, and "it may or may not have been" is not an answer they can act on. So this throws,
     * with the downstream status, and the caller decides what the user is told.
     */
    protected String post(String path, Object body, String bearerToken) {
        URI url = UriComponentsBuilder.fromUriString(baseUrl + path).build().encode().toUri();
        try {
            return restClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            // The URL is logged, the token never is, and the body is not either: a 4xx from a
            // validation failure can echo the request back, which is the caller's data.
            LOG.warn("{} write rejected for {} with status {}", label, path, ex.getStatusCode().value());
            throw new DownstreamWriteException(label, ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (RuntimeException ex) {
            LOG.warn("{} write failed for {}: {}", label, path, ex.getMessage());
            throw new DownstreamWriteException(label, 0, null);
        }
    }

    /**
     * Deletes on a downstream service, with the same failure rule as {@link #post}: it throws, so a
     * caller that is undoing something knows whether the undo happened.
     */
    protected void delete(String path, String bearerToken) {
        URI url = UriComponentsBuilder.fromUriString(baseUrl + path).build().encode().toUri();
        try {
            restClient.delete()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            LOG.warn("{} delete rejected for {} with status {}", label, path, ex.getStatusCode().value());
            throw new DownstreamWriteException(label, ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (RuntimeException ex) {
            LOG.warn("{} delete failed for {}: {}", label, path, ex.getMessage());
            throw new DownstreamWriteException(label, 0, null);
        }
    }

    /** A write the downstream service did not accept, with enough for the caller to say why. */
    public static class DownstreamWriteException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final String service;
        private final int status;
        private final String responseBody;

        public DownstreamWriteException(String service, int status, String responseBody) {
            super(service + " write failed with status " + status);
            this.service = service;
            this.status = status;
            this.responseBody = responseBody;
        }

        public String service() {
            return service;
        }

        /** Zero when the service could not be reached at all. */
        public int status() {
            return status;
        }

        public String responseBody() {
            return responseBody;
        }
    }

    protected static Map<String, Object> params() {
        return new LinkedHashMap<>();
    }
}
