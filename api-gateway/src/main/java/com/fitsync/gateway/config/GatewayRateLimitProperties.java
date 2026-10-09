package com.fitsync.gateway.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The gateway's per-caller request limits, counted in Redis over a fixed window.
 *
 * <p>{@code generalLimit} applies to every request: per user once the caller is authenticated, per client
 * address before that. {@code authLimit} is the stricter one for unauthenticated calls to
 * {@code /api/v1/auth/**}, counted per client address. {@code refreshLimit} is the separate, looser one for
 * {@code POST /api/v1/auth/refresh}, which is called without a valid access token by design and by every
 * session behind one address, so it must not drain the sign-in counter. {@code trustedProxyHops} is how many proxies in front of
 * the gateway append to {@code X-Forwarded-For} (CloudFront and the ALB in the cloud, so 2; none locally, so 0);
 * see {@code ClientAddressResolver} for how the client address is read. {@code redisBackoff} is how long the
 * gateway stops asking Redis after a failure, letting every request through meanwhile.
 *
 * <p>A missing or non-positive number falls back to its default, so a typo in an environment variable can
 * never turn into a limit of zero.
 */
@ConfigurationProperties(prefix = "fitsync.gateway.rate-limit")
public record GatewayRateLimitProperties(
        boolean enabled,
        Duration window,
        int generalLimit,
        int authLimit,
        int refreshLimit,
        int trustedProxyHops,
        Duration redisBackoff) {

    static final Duration DEFAULT_WINDOW = Duration.ofMinutes(1);
    static final int DEFAULT_GENERAL_LIMIT = 120;
    static final int DEFAULT_AUTH_LIMIT = 10;
    static final int DEFAULT_REFRESH_LIMIT = 60;
    static final Duration DEFAULT_REDIS_BACKOFF = Duration.ofSeconds(30);

    public GatewayRateLimitProperties {
        window = window == null || window.isZero() || window.isNegative() ? DEFAULT_WINDOW : window;
        generalLimit = generalLimit > 0 ? generalLimit : DEFAULT_GENERAL_LIMIT;
        authLimit = authLimit > 0 ? authLimit : DEFAULT_AUTH_LIMIT;
        refreshLimit = refreshLimit > 0 ? refreshLimit : DEFAULT_REFRESH_LIMIT;
        trustedProxyHops = Math.max(trustedProxyHops, 0);
        redisBackoff = redisBackoff == null || redisBackoff.isNegative() ? DEFAULT_REDIS_BACKOFF : redisBackoff;
    }
}
