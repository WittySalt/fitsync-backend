package com.fitsync.analytics.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * A cache that cannot reach Redis must not fail the read: {@link CacheConfig}'s error handler logs the
 * failure and the cached method runs as on a miss.
 */
@SpringJUnitConfig(CacheConfigErrorHandlingTests.TestConfig.class)
class CacheConfigErrorHandlingTests {

    @Autowired
    private DashboardReader reader;

    @Test
    void readSucceedsWhenTheCacheBackendIsDown() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        assertThat(reader.read(userId)).isEqualTo("dashboard-" + userId);
        assertThat(reader.read(userId)).isEqualTo("dashboard-" + userId);

        // Every call reached the method: nothing was cached, and nothing was thrown.
        assertThat(reader.calls()).isEqualTo(2);
    }

    @Configuration
    @Import(CacheConfig.class)
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        CacheManager cacheManager() {
            SimpleCacheManager manager = new SimpleCacheManager();
            manager.setCaches(List.of(new UnreachableCache(CacheConfig.DASHBOARD_CACHE)));
            return manager;
        }

        @Bean
        DashboardReader dashboardReader() {
            return new DashboardReader();
        }
    }

    static class DashboardReader {
        private final AtomicInteger calls = new AtomicInteger();

        // A method, not the field: the caching proxy is a subclass whose own fields stay empty.
        public int calls() {
            return calls.get();
        }

        @Cacheable(cacheNames = CacheConfig.DASHBOARD_CACHE, key = "#userId")
        public String read(UUID userId) {
            calls.incrementAndGet();
            return "dashboard-" + userId;
        }
    }

    /** Behaves like a Redis cache whose server is gone: every operation throws. */
    static class UnreachableCache extends AbstractValueAdaptingCache {
        private final String name;

        UnreachableCache(String name) {
            super(false);
            this.name = name;
        }

        private static RedisConnectionFailureException down() {
            return new RedisConnectionFailureException("Unable to connect to Redis");
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object getNativeCache() {
            return this;
        }

        @Override
        protected Object lookup(Object key) {
            throw down();
        }

        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            throw down();
        }

        @Override
        public void put(Object key, Object value) {
            throw down();
        }

        @Override
        public void evict(Object key) {
            throw down();
        }

        @Override
        public void clear() {
            throw down();
        }

        @Override
        public Cache.ValueWrapper putIfAbsent(Object key, Object value) {
            throw down();
        }
    }
}
