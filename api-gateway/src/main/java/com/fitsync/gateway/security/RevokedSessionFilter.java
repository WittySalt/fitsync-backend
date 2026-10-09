package com.fitsync.gateway.security;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fitsync.gateway.dto.ApiError;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects an application token whose session has been revoked, with 401 and code {@code SESSION_REVOKED}.
 *
 * <p>It runs right after {@link JwtAuthenticationFilter}, so it only sees tokens whose signature and expiry have
 * already been checked. auth-service writes {@code fitsync:revoked-sid:<sid>} to Redis when it revokes a session,
 * for as long as an access token of that session can still be unexpired.
 *
 * <ul>
 *   <li>A token without a {@code sid} claim was issued before sessions existed. It is let through until it
 *       expires, so a deploy does not sign everyone out.</li>
 *   <li>When Redis cannot be reached the request is let through (fail open), Redis is not asked again for a few
 *       seconds, and a warning is logged at most once a minute. Revocation is then still enforced within one
 *       access-token lifetime (15 minutes by default), because auth-service refuses the session's next refresh.
 *       Failing closed would instead take the whole API down with Redis.</li>
 * </ul>
 */
@Component
public class RevokedSessionFilter extends OncePerRequestFilter {

    public static final String KEY_PREFIX = "fitsync:revoked-sid:";
    public static final String SESSION_REVOKED = "SESSION_REVOKED";

    private static final Logger LOGGER = LoggerFactory.getLogger(RevokedSessionFilter.class);
    private static final long BACKOFF_MILLIS = 5_000;
    private static final long WARN_INTERVAL_MILLIS = 60_000;

    private final JwtService jwtService;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectWriter apiErrorWriter;
    private final AtomicLong skipRedisUntil = new AtomicLong();
    private final AtomicLong lastWarningAt = new AtomicLong();

    public RevokedSessionFilter(JwtService jwtService,
                                ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.redisTemplateProvider = redisTemplateProvider;
        this.apiErrorWriter = objectMapper.writerFor(ApiError.class);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        UUID sessionId = sessionIdOf(SecurityContextHolder.getContext().getAuthentication());
        if (sessionId != null && isRevoked(sessionId)) {
            SecurityContextHolder.clearContext();
            reject(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** The session of an authenticated application token; {@code null} for anything else or no {@code sid}. */
    private UUID sessionIdOf(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof GatewayUserPrincipal)
                || !(authentication.getCredentials() instanceof String token)) {
            return null;
        }
        try {
            return jwtService.extractSessionId(token);
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean isRevoked(UUID sessionId) {
        if (System.currentTimeMillis() < skipRedisUntil.get()) {
            return false;
        }
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + sessionId));
        } catch (RuntimeException ex) {
            long now = System.currentTimeMillis();
            skipRedisUntil.set(now + BACKOFF_MILLIS);
            warn(now);
            return false;
        }
    }

    /** A fixed message: nothing from the request or the exception goes into the log line. */
    private void warn(long now) {
        long last = lastWarningAt.get();
        if (now - last >= WARN_INTERVAL_MILLIS && lastWarningAt.compareAndSet(last, now)) {
            LOGGER.warn("Redis is unreachable; revoked sessions are not checked at the gateway until it is back,"
                    + " and are refused at their next refresh instead");
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        apiErrorWriter.writeValue(response.getWriter(), new ApiError(
                SESSION_REVOKED,
                "The session has been revoked; sign in again",
                request.getRequestURI(),
                Instant.now(),
                request.getHeader("X-Correlation-Id")));
    }

    /** Ends the back-off after a Redis failure; for tests that switch Redis between down and up. */
    void resetRedisBackoff() {
        skipRedisUntil.set(0);
    }
}
