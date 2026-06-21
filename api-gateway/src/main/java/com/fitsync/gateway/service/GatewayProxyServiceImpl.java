package com.fitsync.gateway.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.fitsync.gateway.config.GatewayRoutesProperties;
import com.fitsync.gateway.exception.DownstreamServiceException;
import com.fitsync.gateway.security.GatewayUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.RequestEntity;
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
import org.springframework.web.util.UriUtils;

@Service
public class GatewayProxyServiceImpl implements GatewayProxyService {

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

    private final RestTemplate restTemplate;
    private final GatewayRoutesProperties routesProperties;

    public GatewayProxyServiceImpl(RestTemplate restTemplate,
                                   GatewayRoutesProperties routesProperties) {
        this.restTemplate = restTemplate;
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

    private ResponseEntity<byte[]> forward(String baseUrl, HttpServletRequest request, byte[] body) {
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        HttpHeaders headers = copyHeaders(request);
        addGatewayHeaders(headers);

        URI targetUri = buildTargetUri(baseUrl, request);
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
        return headers;
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
        URI baseUri = URI.create(normalizeBaseUrl(baseUrl));
        validateBaseUri(baseUri);

        String requestUri = request.getRequestURI();
        if (!StringUtils.hasText(requestUri) || !requestUri.startsWith("/")) {
            throw new IllegalArgumentException("Proxy request URI must be an absolute path");
        }
        if (containsPathTraversalSegment(requestUri)) {
            throw new IllegalArgumentException("Proxy request URI must not contain path traversal segments");
        }

        return UriComponentsBuilder.fromUri(baseUri)
                .replacePath(requestUri)
                .replaceQuery(request.getQueryString())
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

    private boolean containsPathTraversalSegment(String requestUri) {
        String decodedPath = UriUtils.decode(requestUri, StandardCharsets.UTF_8);
        for (String segment : decodedPath.split("/")) {
            if ("..".equals(segment)) {
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
