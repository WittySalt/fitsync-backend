package com.fitsync.web.cache;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Lets a cached method fall back to its own work when the cache store fails. A failed read counts as a miss,
 * so the method runs and answers from the database; a failed write, eviction or clear is dropped. Spring's
 * default handler rethrows instead, which would turn a Redis outage into a failed request.
 *
 * <p>The warning is logged at most once per interval, so an outage does not log once per request. Neither the
 * key (it holds the user's search text) nor the exception message is logged, only the cache name, the
 * operation and the exception type.
 */
public class FailOpenCacheErrorHandler implements CacheErrorHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FailOpenCacheErrorHandler.class);
    private static final Duration DEFAULT_LOG_INTERVAL = Duration.ofSeconds(30);

    private final Clock clock;
    private final long logIntervalMillis;
    private final AtomicLong nextWarningAtMillis = new AtomicLong();

    public FailOpenCacheErrorHandler() {
        this(Clock.systemUTC(), DEFAULT_LOG_INTERVAL);
    }

    FailOpenCacheErrorHandler(Clock clock, Duration logInterval) {
        this.clock = clock;
        this.logIntervalMillis = logInterval.toMillis();
    }

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        warn("read", cache, exception);
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        warn("write", cache, exception);
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        warn("eviction", cache, exception);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        warn("clear", cache, exception);
    }

    /** True when this call logged at warning level; the others in the same interval log at debug. */
    boolean warn(String operation, Cache cache, RuntimeException exception) {
        long now = clock.millis();
        long next = nextWarningAtMillis.get();
        String cacheName = sanitizeForLog(cache == null ? "?" : cache.getName());
        String failure = sanitizeForLog(exception.getClass().getSimpleName());
        String step = sanitizeForLog(operation);
        if (now >= next && nextWarningAtMillis.compareAndSet(next, now + logIntervalMillis)) {
            LOGGER.warn("Cache {} failed on {}; carrying on without it ({})", cacheName, step, failure);
            return true;
        }
        LOGGER.debug("Cache {} failed on {} ({})", cacheName, step, failure);
        return false;
    }

    private static String sanitizeForLog(String value) {
        return String.valueOf(value).replace("\r", "_").replace("\n", "_");
    }
}
