package com.fitsync.workout.config;

import java.time.Duration;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.web.cache.FailOpenCacheErrorHandler;
import com.fitsync.web.response.PageResponse;
import com.fitsync.workout.dto.ExerciseView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * The shared exercise catalogue search cache, kept in Redis with a TTL (one hour by default).
 *
 * <p>Built the way analytics-service builds its caches (a {@link RedisCacheManagerBuilderCustomizer} and a
 * Jackson serializer made from the application's {@link ObjectMapper}, so records and {@code Instant}s
 * serialize as they do over HTTP), with two differences. The serializer is bound to the one type this cache
 * holds instead of writing a class name into every entry, so nothing read back from Redis can name a class to
 * instantiate. And clearing the cache walks its keys with SCAN rather than KEYS, so an import never blocks
 * Redis.
 *
 * <p>A cache failure never fails a request: {@link FailOpenCacheErrorHandler} turns it into a database read.
 */
@Configuration
@EnableCaching
public class CatalogueCacheConfig implements CachingConfigurer {

    public static final String EXERCISE_SEARCH_CACHE = "workoutExerciseCatalogueSearch";

    @Override
    public CacheErrorHandler errorHandler() {
        return new FailOpenCacheErrorHandler();
    }

    @Bean
    RedisCacheManagerBuilderCustomizer catalogueRedisCacheManagerBuilderCustomizer(
            ObjectMapper objectMapper,
            RedisConnectionFactory connectionFactory,
            @Value("${fitsync.catalogue-cache.ttl:PT1H}") Duration ttl) {
        JavaType pageOfExercises = objectMapper.getTypeFactory()
                .constructParametricType(PageResponse.class, ExerciseView.class);
        RedisSerializationContext.SerializationPair<Object> values = RedisSerializationContext.SerializationPair
                .fromSerializer(new Jackson2JsonRedisSerializer<>(objectMapper.copy(), pageOfExercises));
        return builder -> builder
                .cacheWriter(RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory, BatchStrategies.scan(500)))
                .withCacheConfiguration(EXERCISE_SEARCH_CACHE, RedisCacheConfiguration.defaultCacheConfig()
                        .disableCachingNullValues()
                        .entryTtl(ttl)
                        .serializeValuesWith(values));
    }
}
