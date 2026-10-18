package com.fitsync.gateway.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.fitsync.gateway.config.GatewayInternalPaths;
import com.fitsync.gateway.config.GatewayRoutesProperties;
import com.fitsync.gateway.exception.AdminRouteNotListedException;
import com.fitsync.gateway.exception.DownstreamServiceException;
import com.fitsync.gateway.security.GatewayUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.RequestEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.util.UriUtils;

/**
 * Forwards each public route to its owning service unchanged in path and query, stripping hop-by-hop
 * and identity headers from the client and replacing the identity headers with the ones derived from the
 * token this gateway has already verified.
 */
@Service
public class GatewayProxyServiceImpl implements GatewayProxyService {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayProxyServiceImpl.class);
    private static final int MAX_DECODE_ROUNDS = 3;

    /**
     * Never copied in either direction. Besides the standard hop-by-hop headers this includes the identity
     * and correlation headers, so a client cannot spoof them: the gateway sets them itself after authenticating.
     */
    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private static final List<String> HOP_BY_HOP_HEADERS = List.of(
            HttpHeaders.HOST.toLowerCase(Locale.ROOT),
            HttpHeaders.CONNECTION.toLowerCase(Locale.ROOT),
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            HttpHeaders.CONTENT_LENGTH.toLowerCase(Locale.ROOT),
            HttpHeaders.TRANSFER_ENCODING.toLowerCase(Locale.ROOT),
            HttpHeaders.UPGRADE.toLowerCase(Locale.ROOT),
            "x-user-id",
            "x-user-email",
            "x-user-roles",
            "x-correlation-id");

    /** Copied in chunks this size and flushed after each one, so an event is not held back. */
    private static final int STREAM_CHUNK_BYTES = 512;

    private static final String ADMIN_ROUTE_PREFIX = "/api/v1/admin/";
    private static final String INTERNAL_ROUTE_PREFIX = "/api/v1/internal";

    /**
     * The fixed set of admin console destinations, keyed by the path segment the console uses.
     *
     * <p>The key is never used to build a host name or URL; it only selects one of these seven
     * already-configured base URLs, so a caller cannot steer a request anywhere else by choosing
     * an unusual segment value.
     */
    private static final Map<String, Function<GatewayRoutesProperties, String>> ADMIN_SERVICE_BASE_URLS = Map.of(
            "auth", GatewayRoutesProperties::authServiceBaseUrl,
            "users", GatewayRoutesProperties::userServiceBaseUrl,
            "workouts", GatewayRoutesProperties::workoutServiceBaseUrl,
            "nutrition", GatewayRoutesProperties::nutritionServiceBaseUrl,
            "analytics", GatewayRoutesProperties::analyticsServiceBaseUrl,
            // The coach lives in recommendation-service; it has no admin surface of its own.
            "coach", GatewayRoutesProperties::recommendationServiceBaseUrl,
            "notifications", GatewayRoutesProperties::notificationServiceBaseUrl);

    /** Rejects an encoded slash or backslash, a literal backslash, or an embedded scheme. */
    private static final Pattern SUSPICIOUS_ADMIN_PATH = Pattern.compile("\\\\|%2f|%5c|://", Pattern.CASE_INSENSITIVE);

    private final RestTemplate restTemplate;
    private final ClientHttpRequestFactory coachRequestFactory;
    private final GatewayRoutesProperties routesProperties;

    public GatewayProxyServiceImpl(RestTemplate restTemplate,
                                   ClientHttpRequestFactory coachRequestFactory,
                                   GatewayRoutesProperties routesProperties) {
        this.restTemplate = restTemplate;
        this.coachRequestFactory = coachRequestFactory;
        this.routesProperties = routesProperties;
    }

    @Override
    public ResponseEntity<byte[]> forwardToAuthService(HttpServletRequest request, byte[] body) {
        return forward(routesProperties.authServiceBaseUrl(), request, body);
    }

    @Override
    public ResponseEntity<byte[]> forwardToUserService(HttpServletRequest request, byte[] body) {
        return forward(routesProperties.userServiceBaseUrl(), request, body);
    }

    @Override
    public ResponseEntity<byte[]> forwardToWorkoutService(HttpServletRequest request, byte[] body) {
        return forward(routesProperties.workoutServiceBaseUrl(), request, body);
    }

    @Override
    public ResponseEntity<byte[]> forwardToNutritionService(HttpServletRequest request, byte[] body) {
        return forward(routesProperties.nutritionServiceBaseUrl(), request, body);
    }

    @Override
    public ResponseEntity<byte[]> forwardToAnalyticsService(HttpServletRequest request, byte[] body) {
        return forward(routesProperties.analyticsServiceBaseUrl(), request, body);
    }

    @Override
    public ResponseEntity<byte[]> forwardToRecommendationService(HttpServletRequest request, byte[] body) {
        return forward(routesProperties.recommendationServiceBaseUrl(), request, body);
    }

    @Override
    public ResponseEntity<byte[]> forwardToAdminService(String service, HttpServletRequest request, byte[] body) {
        Function<GatewayRoutesProperties, String> baseUrlLookup = ADMIN_SERVICE_BASE_URLS.get(service);
        if (baseUrlLookup == null) {
            throw new IllegalArgumentException("Unknown admin service");
        }

        String rewrittenPath = rewriteAdminPath(service, request.getRequestURI());
        // The decision for every internal path is the list in GatewayInternalPaths; anything else is not found.
        if (!GatewayInternalPaths.isForwardable(service, rewrittenPath.substring(INTERNAL_ROUTE_PREFIX.length()))) {
            throw new AdminRouteNotListedException("Unknown admin route");
        }
        return forward(baseUrlLookup.apply(routesProperties), request, body, rewrittenPath);
    }

    /**
     * Turns {@code /api/v1/admin/{service}/**} into the {@code /api/v1/internal/**} path the
     * service itself exposes, after checking the raw (still-encoded) path for the traversal and
     * obfuscation tricks that a decode-then-check could miss.
     */
    private String rewriteAdminPath(String service, String requestUri) {
        if (SUSPICIOUS_ADMIN_PATH.matcher(requestUri).find() || containsPathTraversalSegment(requestUri)) {
            throw new IllegalArgumentException(
                    "Admin route path must not contain traversal segments, encoded separators, or a scheme");
        }

        String servicePrefix = ADMIN_ROUTE_PREFIX + service;
        if (!requestUri.startsWith(servicePrefix)
                || (requestUri.length() > servicePrefix.length() && requestUri.charAt(servicePrefix.length()) != '/')) {
            throw new IllegalArgumentException("Admin route path did not match the resolved service");
        }

        String suffix = requestUri.substring(servicePrefix.length());
        return INTERNAL_ROUTE_PREFIX + suffix;
    }

    @Override
    public ResponseEntity<StreamingResponseBody> forwardToCoachService(HttpServletRequest request, byte[] body) {
        // The coach lives in recommendation-service; it is the timeout and the streaming that differ,
        // not the destination.
        return forwardToCoach(routesProperties.recommendationServiceBaseUrl(), request, body);
    }

    /**
     * Passes a coach response through as it arrives.
     *
     * <p>The ordinary forward collects a whole response body and then sends it, which is right for
     * every route that answers in one piece and useless for a stream: the client would wait out the
     * entire turn and then receive all the progress events at once, describing work that had already
     * finished.
     *
     * <p>So the downstream response is executed here and its body handed to the servlet container as
     * a body still being written. The downstream response closes when the copy finishes, including
     * when the client disconnects halfway and the write throws.
     */
    private ResponseEntity<StreamingResponseBody> forwardToCoach(String baseUrl, HttpServletRequest request, byte[] body) {
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        HttpHeaders headers = copyHeaders(request);
        addGatewayHeaders(headers);
        URI targetUri = buildTargetUri(baseUrl, request);

        ClientHttpResponse response;
        try {
            ClientHttpRequest downstream = coachRequestFactory.createRequest(targetUri, method);
            downstream.getHeaders().putAll(headers);
            if (body != null && body.length > 0) {
                downstream.getBody().write(body);
            }
            response = downstream.execute();
        } catch (IOException ex) {
            throw new DownstreamServiceException("Downstream service is temporarily unavailable", ex);
        }

        HttpStatusCode status;
        HttpHeaders responseHeaders;
        try {
            status = response.getStatusCode();
            responseHeaders = filterResponseHeaders(response.getHeaders());
        } catch (IOException ex) {
            closeQuietly(response);
            throw new DownstreamServiceException("Downstream service is temporarily unavailable", ex);
        }

        StreamingResponseBody stream = outputStream -> {
            try (ClientHttpResponse open = response; InputStream in = open.getBody()) {
                byte[] buffer = new byte[STREAM_CHUNK_BYTES];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                    // Without this the container decides when to send, and a progress event that
                    // arrives while the turn is still running would sit in a buffer until the turn
                    // ended, which is exactly the delay streaming exists to remove.
                    outputStream.flush();
                }
            }
        };

        return ResponseEntity.status(status).headers(responseHeaders).body(stream);
    }

    private static void closeQuietly(ClientHttpResponse response) {
        try {
            response.close();
        } catch (RuntimeException ex) {
            LOG.debug("Closing a failed downstream response", ex);
        }
    }

    private ResponseEntity<byte[]> forward(String baseUrl, HttpServletRequest request, byte[] body) {
        return forward(baseUrl, request, body, request.getRequestURI());
    }

    private ResponseEntity<byte[]> forward(String baseUrl, HttpServletRequest request, byte[] body,
                                           String targetRequestUri) {
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        HttpHeaders headers = copyHeaders(request);
        addGatewayHeaders(headers);

        URI targetUri = buildTargetUri(baseUrl, targetRequestUri, request.getQueryString());
        RequestEntity<byte[]> requestEntity = new RequestEntity<>(
                body == null ? new byte[0] : body,
                headers,
                method,
                targetUri);

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(requestEntity, byte[].class);
            return ResponseEntity.status(response.getStatusCode())
                    .headers(filterResponseHeaders(response.getHeaders()))
                    .body(response.getBody());
        } catch (RestClientResponseException ex) {
            return ResponseEntity.status(HttpStatusCode.valueOf(ex.getRawStatusCode()))
                    .headers(filterResponseHeaders(ex.getResponseHeaders()))
                    .body(ex.getResponseBodyAsByteArray());
        } catch (ResourceAccessException ex) {
            throw new DownstreamServiceException("Downstream service is temporarily unavailable", ex);
        }
    }

    private HttpHeaders copyHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (isHopByHopHeader(headerName)) {
                continue;
            }

            Enumeration<String> values = request.getHeaders(headerName);
            while (values.hasMoreElements()) {
                headers.add(headerName, values.nextElement());
            }
        }
        appendForwardedFor(headers, request.getRemoteAddr());
        return headers;
    }

    /**
     * Adds the address this request came from to {@code X-Forwarded-For}, as any proxy does. A service behind
     * the gateway trusts only the hops it knows (private addresses), so it reads the client from the last
     * untrusted entry; without this, a client calling the gateway directly could put any address there.
     */
    private static void appendForwardedFor(HttpHeaders headers, String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return;
        }
        List<String> forwardedFor = headers.get(FORWARDED_FOR);
        headers.set(FORWARDED_FOR, forwardedFor == null || forwardedFor.isEmpty()
                ? remoteAddress
                : String.join(", ", forwardedFor) + ", " + remoteAddress);
    }

    private HttpHeaders filterResponseHeaders(HttpHeaders downstreamHeaders) {
        HttpHeaders headers = new HttpHeaders();
        if (downstreamHeaders == null) {
            return headers;
        }

        downstreamHeaders.forEach((name, values) -> {
            if (!isHopByHopHeader(name)) {
                headers.put(name, List.copyOf(values));
            }
        });
        return headers;
    }

    private boolean isHopByHopHeader(String headerName) {
        if (!StringUtils.hasText(headerName)) {
            return false;
        }
        return HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase(Locale.ROOT));
    }

    private void addGatewayHeaders(HttpHeaders headers) {
        String correlationId = headers.getFirst("X-Correlation-Id");
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }
        headers.set("X-Correlation-Id", correlationId);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof GatewayUserPrincipal gatewayUserPrincipal) {
            headers.set("X-User-Id", gatewayUserPrincipal.userId().toString());
            headers.set("X-User-Email", gatewayUserPrincipal.email());
            headers.set("X-User-Roles", gatewayUserPrincipal.role());
        }
    }

    private URI buildTargetUri(String baseUrl, HttpServletRequest request) {
        return buildTargetUri(baseUrl, request.getRequestURI(), request.getQueryString());
    }

    private URI buildTargetUri(String baseUrl, String requestUri, String queryString) {
        URI baseUri = URI.create(normalizeBaseUrl(baseUrl));
        validateBaseUri(baseUri);

        if (!StringUtils.hasText(requestUri) || !requestUri.startsWith("/")) {
            throw new IllegalArgumentException("Proxy request URI must be an absolute path");
        }
        if (containsPathTraversalSegment(requestUri)) {
            throw new IllegalArgumentException("Proxy request URI must not contain path traversal segments");
        }

        return UriComponentsBuilder.fromUri(baseUri)
                .replacePath(requestUri)
                .replaceQuery(queryString)
                .build(true)
                .toUri();
    }

    private void validateBaseUri(URI baseUri) {
        String scheme = baseUri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Gateway route base URL must use HTTP or HTTPS");
        }
        if (!StringUtils.hasText(baseUri.getHost()) || baseUri.getUserInfo() != null) {
            throw new IllegalArgumentException("Gateway route base URL must include only a host and optional port");
        }
        if (StringUtils.hasText(baseUri.getQuery()) || StringUtils.hasText(baseUri.getFragment())) {
            throw new IllegalArgumentException("Gateway route base URL must not include query or fragment components");
        }
    }

    /**
     * True when the path could climb out of the route it was matched on once a downstream server
     * normalises it. The path is decoded until it stops changing, so a double-encoded {@code %252e%252e}
     * cannot hide a dot segment, and each segment is checked without its {@code ;} parameters, which
     * Tomcat drops before it resolves {@code ..}. A backslash is refused because some servers treat it
     * as a separator.
     */
    private boolean containsPathTraversalSegment(String requestUri) {
        String path = requestUri;
        for (int round = 0; round < MAX_DECODE_ROUNDS; round++) {
            String decoded = UriUtils.decode(path, StandardCharsets.UTF_8);
            if (decoded.equals(path)) {
                break;
            }
            path = decoded;
        }
        if (path.indexOf('\\') >= 0) {
            return true;
        }
        for (String segment : path.split("/")) {
            int parameters = segment.indexOf(';');
            String name = parameters >= 0 ? segment.substring(0, parameters) : segment;
            if ("..".equals(name) || ".".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.trim();
        while (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        return normalizedBaseUrl;
    }
}
