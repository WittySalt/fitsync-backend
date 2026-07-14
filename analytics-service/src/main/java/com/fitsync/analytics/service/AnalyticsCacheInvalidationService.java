package com.fitsync.analytics.service;

import com.fitsync.analytics.config.CacheConfig;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsCacheInvalidationService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsCacheInvalidationService.class);
    private static final long SCAN_COUNT = 1000L;
    private static final String REDIS_CACHE_SEPARATOR = "::";
    private static final List<String> USER_SCOPED_CACHE_NAMES = List.of(
            CacheConfig.DASHBOARD_CACHE,
            CacheConfig.BODY_TRENDS_CACHE,
            CacheConfig.WORKOUT_SUMMARY_CACHE,
            CacheConfig.WORKOUT_SERIES_CACHE,
            CacheConfig.NUTRITION_SUMMARY_CACHE,
            CacheConfig.NUTRITION_SERIES_CACHE,
            CacheConfig.COMPARE_CACHE,
            CacheConfig.ACTIVITY_CALENDAR_CACHE,
            CacheConfig.GOAL_PROGRESS_CACHE,
            CacheConfig.STREAKS_CACHE);

    private final StringRedisTemplate redisTemplate;
    private final CacheManager cacheManager;
    private final boolean redisInvalidationEnabled;

    @Autowired
    public AnalyticsCacheInvalidationService(StringRedisTemplate redisTemplate, CacheManager cacheManager) {
        this(redisTemplate, cacheManager, cacheManager instanceof RedisCacheManager);
    }

    AnalyticsCacheInvalidationService(StringRedisTemplate redisTemplate,
                                      CacheManager cacheManager,
                                      boolean redisInvalidationEnabled) {
        this.redisTemplate = redisTemplate;
        this.cacheManager = cacheManager;
        this.redisInvalidationEnabled = redisInvalidationEnabled;
    }

    public void evictForUser(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        for (String cacheName : USER_SCOPED_CACHE_NAMES) {
            evictRedisEntriesForUser(cacheName, userId);
            evictConcurrentMapEntriesForUser(cacheName, userId);
        }
    }

    private void evictRedisEntriesForUser(String cacheName, UUID userId) {
        if (!redisInvalidationEnabled) {
            return;
        }
        String keyPrefix = cacheName + REDIS_CACHE_SEPARATOR + userId;
        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(keyPrefix + "*")
                .count(SCAN_COUNT)
                .build();
        try {
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                List<byte[]> keysToUnlink = new ArrayList<>();
                try (Cursor<byte[]> cursor = connection.scan(scanOptions)) {
                    while (cursor.hasNext()) {
                        byte[] key = cursor.next();
                        if (matchesPrefix(key, keyPrefix)) {
                            keysToUnlink.add(key);
                        }
                    }
                }
                if (!keysToUnlink.isEmpty()) {
                    connection.unlink(keysToUnlink.toArray(byte[][]::new));
                }
                return null;
            });
        } catch (RuntimeException ex) {
            log.warn("Analytics Redis cache invalidation failed cacheName={} userId={} exceptionType={} exceptionMessage={}",
                    cacheName, userId, ex.getClass().getName(), sanitizeForLog(ex.getMessage()));
        }
    }

    private void evictConcurrentMapEntriesForUser(String cacheName, UUID userId) {
        Cache cache = cacheManager.getCache(cacheName);
        if (!(cache instanceof ConcurrentMapCache concurrentMapCache)) {
            return;
        }

        ConcurrentMap<Object, Object> nativeCache = concurrentMapCache.getNativeCache();
        List<Object> cacheKeys = nativeCache.keySet().stream()
                .filter(key -> isUserScopedKey(key, userId))
                .toList();
        cacheKeys.forEach(cache::evictIfPresent);
    }

    private static boolean matchesPrefix(byte[] key, String keyPrefix) {
        return new String(key, StandardCharsets.UTF_8).startsWith(keyPrefix);
    }

    private static boolean isUserScopedKey(Object key, UUID userId) {
        if (key instanceof UUID uuid) {
            return uuid.equals(userId);
        }
        return String.valueOf(key).startsWith(userId.toString());
    }

    private static String sanitizeForLog(String value) {
        return String.valueOf(value)
                .replace("\r", "_")
                .replace("\n", "_");
    }
}
