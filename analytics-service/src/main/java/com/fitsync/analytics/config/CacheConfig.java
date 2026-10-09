package com.fitsync.analytics.config;

import java.time.Duration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * Names the per-user read caches and gives each its Redis TTL: 5 minutes for the dashboard, goals, streaks
 * and achievements, 10 for the rest. Values are stored as JSON with their type, so cached records come back
 * as their own classes. Events evict a user's entries through {@code AnalyticsCacheInvalidationService}.
 *
 * <p>The cache is an optimisation, never a dependency: when Redis cannot be reached (or an entry cannot be
 * read back) the error is logged and the call goes to the read model as if the entry were missing, so a
 * cache outage costs latency, not failed requests.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler(LogFactory.getLog(CacheConfig.class), false);
    }

    public static final String DASHBOARD_CACHE = "analyticsDashboard";
    public static final String BODY_TRENDS_CACHE = "analyticsBodyTrends";
    public static final String WORKOUT_SUMMARY_CACHE = "analyticsWorkoutSummary";
    public static final String WORKOUT_SERIES_CACHE = "analyticsWorkoutSeries";
    public static final String NUTRITION_SUMMARY_CACHE = "analyticsNutritionSummary";
    public static final String NUTRITION_SERIES_CACHE = "analyticsNutritionSeries";
    public static final String COMPARE_CACHE = "analyticsCompare";
    public static final String ACTIVITY_CALENDAR_CACHE = "analyticsActivityCalendar";
    public static final String GOAL_PROGRESS_CACHE = "analyticsGoalProgress";
    public static final String STREAKS_CACHE = "analyticsStreaks";
    public static final String ACHIEVEMENTS_CACHE = "analyticsAchievements";
    public static final String WEEKLY_REPORT_CACHE = "analyticsWeeklyReport";

    @Bean
    RedisCacheManagerBuilderCustomizer analyticsRedisCacheManagerBuilderCustomizer(ObjectMapper objectMapper) {
        ObjectMapper cacheObjectMapper = objectMapper.copy();
        cacheObjectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY);
        GenericJackson2JsonRedisSerializer.registerNullValueSerializer(cacheObjectMapper, "@class");

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(cacheObjectMapper);
        RedisSerializationContext.SerializationPair<Object> valuePair =
                RedisSerializationContext.SerializationPair.fromSerializer(serializer);

        return builder -> builder
                .withCacheConfiguration(
                        DASHBOARD_CACHE,
                        org.springframework.data.redis.cache.RedisCacheConfiguration.defaultCacheConfig()
                                .disableCachingNullValues()
                                .entryTtl(Duration.ofMinutes(5))
                                .serializeValuesWith(valuePair))
                .withCacheConfiguration(
                        BODY_TRENDS_CACHE,
                        org.springframework.data.redis.cache.RedisCacheConfiguration.defaultCacheConfig()
                                .disableCachingNullValues()
                                .entryTtl(Duration.ofMinutes(10))
                                .serializeValuesWith(valuePair))
                .withCacheConfiguration(
                        WORKOUT_SUMMARY_CACHE,
                        org.springframework.data.redis.cache.RedisCacheConfiguration.defaultCacheConfig()
                                .disableCachingNullValues()
                                .entryTtl(Duration.ofMinutes(10))
                                .serializeValuesWith(valuePair))
                .withCacheConfiguration(
                        WORKOUT_SERIES_CACHE,
                        org.springframework.data.redis.cache.RedisCacheConfiguration.defaultCacheConfig()
                                .disableCachingNullValues()
                                .entryTtl(Duration.ofMinutes(10))
                                .serializeValuesWith(valuePair))
                .withCacheConfiguration(
                        NUTRITION_SUMMARY_CACHE,
                        org.springframework.data.redis.cache.RedisCacheConfiguration.defaultCacheConfig()
                                .disableCachingNullValues()
                                .entryTtl(Duration.ofMinutes(10))
                                .serializeValuesWith(valuePair))
                .withCacheConfiguration(
                        NUTRITION_SERIES_CACHE,
                        org.springframework.data.redis.cache.RedisCacheConfiguration.defaultCacheConfig()
                                .disableCachingNullValues()
                                .entryTtl(Duration.ofMinutes(10))
                                .serializeValuesWith(valuePair))
                .withCacheConfiguration(
                        COMPARE_CACHE,
                        org.springframework.data.redis.cache.RedisCacheConfiguration.defaultCacheConfig()
                                .disableCachingNullValues()
                                .entryTtl(Duration.ofMinutes(10))
                                .serializeValuesWith(valuePair))
                .withCacheConfiguration(
                        ACTIVITY_CALENDAR_CACHE,
                        org.springframework.data.redis.cache.RedisCacheConfiguration.defaultCacheConfig()
                                .disableCachingNullValues()
                                .entryTtl(Duration.ofMinutes(10))
                                .serializeValuesWith(valuePair))
                .withCacheConfiguration(
                        GOAL_PROGRESS_CACHE,
                        org.springframework.data.redis.cache.RedisCacheConfiguration.defaultCacheConfig()
                                .disableCachingNullValues()
                                .entryTtl(Duration.ofMinutes(5))
                                .serializeValuesWith(valuePair))
                .withCacheConfiguration(
                        STREAKS_CACHE,
                        org.springframework.data.redis.cache.RedisCacheConfiguration.defaultCacheConfig()
                                .disableCachingNullValues()
                                .entryTtl(Duration.ofMinutes(5))
                                .serializeValuesWith(valuePair))
                .withCacheConfiguration(
                        ACHIEVEMENTS_CACHE,
                        org.springframework.data.redis.cache.RedisCacheConfiguration.defaultCacheConfig()
                                .disableCachingNullValues()
                                .entryTtl(Duration.ofMinutes(5))
                                .serializeValuesWith(valuePair))
                .withCacheConfiguration(
                        WEEKLY_REPORT_CACHE,
                        org.springframework.data.redis.cache.RedisCacheConfiguration.defaultCacheConfig()
                                .disableCachingNullValues()
                                .entryTtl(Duration.ofMinutes(10))
                                .serializeValuesWith(valuePair));
    }
}
