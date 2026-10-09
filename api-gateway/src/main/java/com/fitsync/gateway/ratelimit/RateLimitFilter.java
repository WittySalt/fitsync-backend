package com.fitsync.gateway.ratelimit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fitsync.gateway.config.GatewayRateLimitProperties;
import com.fitsync.gateway.dto.ApiError;
import com.fitsync.gateway.security.GatewayUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Counts each request against its caller's limit and answers 429 once the limit is reached.
 *
 * <p>Runs inside the security chain after authentication and before authorization, so an authenticated request
 * is counted under its user id (the same limit across devices and addresses), and an unauthenticated one under
 * its client address. Unauthenticated calls to {@code /api/v1/auth/**} (sign-in, bootstrap, cancelling a
 * deletion) have their own, stricter counter. The coach routes keep their own database-backed limiter
 * downstream and are only under the general limit here.
 *
 * <p>Not counted: the ALB health check ({@code /actuator/health} and its sub-paths), CORS preflights, and
 * the async and error dispatches that follow a request already counted.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    static final String KEY_PREFIX = "fitsync:ratelimit:";
    static final String LIMIT_HEADER = "X-RateLimit-Limit";
    static final String REMAINING_HEADER = "X-RateLimit-Remaining";

    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";
    private static final String HEALTH_PATH = "/actuator/health";

    private final RedisRateLimiter rateLimiter;
    private final GatewayRateLimitProperties properties;
    private final ClientAddressResolver clientAddressResolver;
    private final ObjectWriter apiErrorWriter;

    public RateLimitFilter(RedisRateLimiter rateLimiter,
                           GatewayRateLimitProperties properties,
                           ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.clientAddressResolver = new ClientAddressResolver(properties.trustedProxyHops());
        this.apiErrorWriter = objectMapper.writerFor(ApiError.class);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.enabled() || HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String path = pathOf(request);
        return HEALTH_PATH.equals(path) || path.startsWith(HEALTH_PATH + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String userKey = authenticatedUserKey();
        String key;
        int limit;
        if (userKey != null) {
            key = KEY_PREFIX + "general:" + userKey;
            limit = properties.generalLimit();
        } else if (pathOf(request).startsWith(AUTH_PATH_PREFIX)) {
            key = KEY_PREFIX + "auth:ip:" + clientAddressResolver.resolve(request);
            limit = properties.authLimit();
        } else {
            key = KEY_PREFIX + "general:ip:" + clientAddressResolver.resolve(request);
            limit = properties.generalLimit();
        }

        RateLimitDecision decision = rateLimiter.tryAcquire(key, limit, properties.window());
        if (decision.counted()) {
            response.setHeader(LIMIT_HEADER, Integer.toString(decision.limit()));
            response.setHeader(REMAINING_HEADER, Long.toString(decision.remaining()));
        }
        if (!decision.allowed()) {
            reject(request, response, decision.retryAfterSeconds());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        apiErrorWriter.writeValue(response.getWriter(), new ApiError(
                "RATE_LIMITED",
                "Too many requests. Try again in " + retryAfterSeconds + " seconds.",
                request.getRequestURI(),
                Instant.now(),
                request.getHeader("X-Correlation-Id")));
    }

    /**
     * The user a request is counted under, or null when it is not authenticated. An application token carries the
     * user id; a Cognito token is counted under a hash of its principal name, so no user name or email ends up in
     * a Redis key.
     */
    private static String authenticatedUserKey() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        if (authentication.getPrincipal() instanceof GatewayUserPrincipal principal && principal.userId() != null) {
            return "user:" + principal.userId();
        }
        String name = authentication.getName();
        return StringUtils.hasText(name) ? "user:h:" + sha256(name) : null;
    }

    /** The decoded path inside the application, so an encoded segment such as {@code %61uth} cannot dodge a rule. */
    private static String pathOf(HttpServletRequest request) {
        return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
