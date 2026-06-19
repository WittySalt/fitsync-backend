package com.fitsync.analytics.config;

import java.time.Duration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String DASHBOARD_CACHE = "analyticsDashboard";
    public static final String BODY_TRENDS_CACHE = "analyticsBodyTrends";
    public static final String WORKOUT_SUMMARY_CACHE = "analyticsWorkoutSummary";
    public static final String WORKOUT_SERIES_CACHE = "analyticsWorkoutSeries";
    public static final String NUTRITION_SUMMARY_CACHE = "analyticsNutritionSummary";
    public static final String NUTRITION_SERIES_CACHE = "analyticsNutritionSeries";
    public static final String COMPARE_CACHE = "analyticsCompare";
    public static final String GOAL_PROGRESS_CACHE = "analyticsGoalProgress";

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
                        GOAL_PROGRESS_CACHE,
                        org.springframework.data.redis.cache.RedisCacheConfiguration.defaultCacheConfig()
                                .disableCachingNullValues()
                                .entryTtl(Duration.ofMinutes(5))
                                .serializeValuesWith(valuePair));
    }
}
