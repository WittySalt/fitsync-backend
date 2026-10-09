package com.fitsync.web.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class FailOpenCacheErrorHandlerTests {

    private final ConcurrentMapCache cache = new ConcurrentMapCache("catalogue");

    @Test
    void swallowsEveryKindOfCacheFailure() {
        FailOpenCacheErrorHandler handler = new FailOpenCacheErrorHandler();
        RuntimeException failure = new IllegalStateException("redis down");

        assertThatCode(() -> {
            handler.handleCacheGetError(failure, cache, "k");
            handler.handleCachePutError(failure, cache, "k", "v");
            handler.handleCacheEvictError(failure, cache, "k");
            handler.handleCacheClearError(failure, cache);
        }).doesNotThrowAnyException();
    }

    @Test
    void warnsOncePerInterval() {
        Instant start = Instant.parse("2026-10-09T10:00:00Z");
        RuntimeException failure = new IllegalStateException("redis down");

        FailOpenCacheErrorHandler first = new FailOpenCacheErrorHandler(Clock.fixed(start, ZoneOffset.UTC),
                Duration.ofSeconds(30));
        assertThat(first.warn("read", cache, failure)).isTrue();
        assertThat(first.warn("read", cache, failure)).isFalse();
    }
}
