package com.fitsync.gateway.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.fitsync.gateway.config.GatewayRateLimitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Fixed-window counters in Redis, one key per policy and caller.
 *
 * <p>The script increments the key and, on the first hit of a window, sets its expiry, in one atomic step, so
 * two gateway instances can never both start a window or leave a counter without an expiry. It returns the new
 * count and the milliseconds left in the window, which becomes {@code Retry-After}.
 *
 * <p>Fails open: when Redis cannot answer (down, slow past the client timeout, wrong password), the request is
 * let through and Redis is left alone for {@code redisBackoff}, so an outage costs one timeout per back-off
 * period rather than one per request. The warning is logged once per back-off period, never per request.
 */
@Component
public class RedisRateLimiter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisRateLimiter.class);

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
              ttl = tonumber(ARGV[1])
            end
            return {count, ttl}
            """, List.class);

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Duration redisBackoff;
    private final Clock clock;
    private final AtomicLong bypassUntilMillis = new AtomicLong();

    @Autowired
    public RedisRateLimiter(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                            GatewayRateLimitProperties properties) {
        this(redisTemplateProvider, properties.redisBackoff(), Clock.systemUTC());
    }

    RedisRateLimiter(ObjectProvider<StringRedisTemplate> redisTemplateProvider, Duration redisBackoff, Clock clock) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.redisBackoff = redisBackoff;
        this.clock = clock;
    }

    public RateLimitDecision tryAcquire(String key, int limit, Duration window) {
        long now = clock.millis();
        if (now < bypassUntilMillis.get()) {
            return RateLimitDecision.unchecked(limit);
        }
        try {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getObject();
            List<?> reply = redisTemplate.execute(INCREMENT_SCRIPT, List.of(key), Long.toString(window.toMillis()));
            if (reply == null || reply.size() < 2) {
                throw new IllegalStateException("Unexpected rate limit script reply");
            }
            long count = toLong(reply.get(0));
            long remainingMillis = toLong(reply.get(1));
            if (count > limit) {
                return RateLimitDecision.denied(limit, (remainingMillis + 999) / 1000);
            }
            return RateLimitDecision.allowed(limit, limit - count);
        } catch (RuntimeException ex) {
            bypassUntilMillis.set(now + redisBackoff.toMillis());
            LOGGER.warn("Rate limiting skipped for {} s because Redis did not answer ({})",
                    redisBackoff.toSeconds(), ex.getClass().getSimpleName());
            return RateLimitDecision.unchecked(limit);
        }
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("Unexpected rate limit script value");
    }
}
