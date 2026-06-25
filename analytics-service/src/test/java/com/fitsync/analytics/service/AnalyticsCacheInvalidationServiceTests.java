package com.fitsync.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fitsync.analytics.config.CacheConfig;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

class AnalyticsCacheInvalidationServiceTests {

    private static final List<String> CACHE_NAMES = List.of(
            CacheConfig.DASHBOARD_CACHE,
            CacheConfig.BODY_TRENDS_CACHE,
            CacheConfig.WORKOUT_SUMMARY_CACHE,
            CacheConfig.WORKOUT_SERIES_CACHE,
            CacheConfig.NUTRITION_SUMMARY_CACHE,
            CacheConfig.NUTRITION_SERIES_CACHE,
            CacheConfig.COMPARE_CACHE,
            CacheConfig.ACTIVITY_CALENDAR_CACHE,
            CacheConfig.GOAL_PROGRESS_CACHE);

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void evictsOnlyTargetUserRedisKeysWithScanAndUnlink() {
        UUID targetUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID otherUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisConnection connection = mock(RedisConnection.class);
        List<ScanOptions> scanOptions = new ArrayList<>();
        List<String> unlinkedKeys = new ArrayList<>();

        when(redisTemplate.execute(any(RedisCallback.class))).thenAnswer(invocation -> {
            RedisCallback callback = invocation.getArgument(0);
            return callback.doInRedis(connection);
        });
        when(connection.scan(any(ScanOptions.class))).thenAnswer(invocation -> {
            ScanOptions options = invocation.getArgument(0);
            scanOptions.add(options);
            String cacheName = options.getPattern().substring(0, options.getPattern().indexOf("::"));
            return cursor(
                    cacheName + "::" + targetUserId + ":2026-06-01:2026-06-30",
                    cacheName + "::" + otherUserId + ":2026-06-01:2026-06-30");
        });
        doAnswer(invocation -> {
            for (Object argument : invocation.getArguments()) {
                unlinkedKeys.add(new String((byte[]) argument, StandardCharsets.UTF_8));
            }
            return (long) invocation.getArguments().length;
        }).when(connection).unlink(any(byte[].class));

        AnalyticsCacheInvalidationService service = new AnalyticsCacheInvalidationService(
                redisTemplate, new ConcurrentMapCacheManager(CACHE_NAMES.toArray(String[]::new)), true);

        service.evictForUser(targetUserId);

        assertThat(scanOptions)
                .extracting(ScanOptions::getPattern)
                .containsExactlyElementsOf(CACHE_NAMES.stream()
                        .map(cacheName -> cacheName + "::" + targetUserId + "*")
                        .toList());
        assertThat(scanOptions)
                .extracting(ScanOptions::getCount)
                .containsOnly(1000L);
        assertThat(unlinkedKeys)
                .hasSize(CACHE_NAMES.size())
                .allMatch(key -> key.contains(targetUserId.toString()))
                .noneMatch(key -> key.contains(otherUserId.toString()));
        verify(redisTemplate, never()).keys(anyString());
        verify(connection, never()).keys(any());
    }

    private static Cursor<byte[]> cursor(String... keys) {
        List<byte[]> values = List.of(keys).stream()
                .map(key -> key.getBytes(StandardCharsets.UTF_8))
                .toList();
        @SuppressWarnings("unchecked")
        Cursor<byte[]> cursor = mock(Cursor.class);
        List<Boolean> hasNextValues = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            hasNextValues.add(true);
        }
        hasNextValues.add(false);
        when(cursor.hasNext()).thenReturn(
                hasNextValues.get(0),
                hasNextValues.subList(1, hasNextValues.size()).toArray(Boolean[]::new));
        when(cursor.next()).thenReturn(values.get(0), values.subList(1, values.size()).toArray(byte[][]::new));
        return cursor;
    }
}
