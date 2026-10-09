package com.fitsync.nutrition.config;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.nutrition.dto.FoodCategoryView;
import com.fitsync.nutrition.service.FoodSearchPage;
import com.fitsync.web.cache.FailOpenCacheErrorHandler;
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
 * The shared food catalogue caches, kept in Redis with a TTL (one hour by default): food search pages and the
 * category tree.
 *
 * <p>Built the way analytics-service builds its caches (a {@link RedisCacheManagerBuilderCustomizer} and a
 * Jackson serializer made from the application's {@link ObjectMapper}), with two differences. Each serializer
 * is bound to the one type its cache holds instead of writing a class name into every entry, so nothing read
 * back from Redis can name a class to instantiate. And clearing a cache walks its keys with SCAN rather than
 * KEYS, so an import never blocks Redis.
 *
 * <p>A cache failure never fails a request: {@link FailOpenCacheErrorHandler} turns it into a database read.
 */
@Configuration
@EnableCaching
public class CatalogueCacheConfig implements CachingConfigurer {

    public static final String FOOD_SEARCH_CACHE = "nutritionFoodCatalogueSearch";
    public static final String FOOD_CATEGORIES_CACHE = "nutritionFoodCategories";

    @Override
    public CacheErrorHandler errorHandler() {
        return new FailOpenCacheErrorHandler();
    }

    @Bean
    RedisCacheManagerBuilderCustomizer catalogueRedisCacheManagerBuilderCustomizer(
            ObjectMapper objectMapper,
            RedisConnectionFactory connectionFactory,
            @Value("${fitsync.catalogue-cache.ttl:PT1H}") Duration ttl) {
        JavaType searchPage = objectMapper.getTypeFactory().constructType(FoodSearchPage.class);
        JavaType categoryTree = objectMapper.getTypeFactory().constructCollectionType(List.class, FoodCategoryView.class);
        return builder -> builder
                .cacheWriter(RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory, BatchStrategies.scan(500)))
                .withCacheConfiguration(FOOD_SEARCH_CACHE, typedConfiguration(objectMapper, searchPage, ttl))
                .withCacheConfiguration(FOOD_CATEGORIES_CACHE, typedConfiguration(objectMapper, categoryTree, ttl));
    }

    private static RedisCacheConfiguration typedConfiguration(ObjectMapper objectMapper, JavaType type, Duration ttl) {
        RedisSerializationContext.SerializationPair<Object> values = RedisSerializationContext.SerializationPair
                .fromSerializer(new Jackson2JsonRedisSerializer<>(objectMapper.copy(), type));
        return RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .entryTtl(ttl)
                .serializeValuesWith(values);
    }
}
