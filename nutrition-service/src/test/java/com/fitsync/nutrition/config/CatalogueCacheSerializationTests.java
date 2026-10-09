package com.fitsync.nutrition.config;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.nutrition.domain.FoodCatalogScope;
import com.fitsync.nutrition.domain.FoodCatalogSource;
import com.fitsync.nutrition.dto.DefaultServingView;
import com.fitsync.nutrition.dto.FoodCategoryView;
import com.fitsync.nutrition.dto.FoodSearchResultView;
import com.fitsync.nutrition.service.FoodSearchPage;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** What the Redis store will hold: plain JSON bound to each cache's type, read back as the same value. */
class CatalogueCacheSerializationTests {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    @Test
    void aSearchPageSurvivesTheRoundTripThroughRedis() {
        FoodSearchPage page = new FoodSearchPage(List.of(new FoodSearchResultView(
                UUID.randomUUID(), "Greek Yogurt", "FAGE", null, "DAIRY", "Dairy", FoodCatalogScope.GLOBAL,
                FoodCatalogSource.IMPORTED, true, 59, "per 100 g", "737628064502", "DAIRY", false,
                new DefaultServingView(UUID.randomUUID(), "1 cup", new BigDecimal("245.00")))),
                0, 20, 1, true);

        Object readBack = roundTrip(CatalogueCacheConfig.FOOD_SEARCH_CACHE, page);

        assertThat(readBack).isEqualTo(page);
        assertThat(((FoodSearchPage) readBack).toPage().getTotalElements()).isEqualTo(1);
    }

    @Test
    void theCategoryTreeSurvivesTheRoundTripThroughRedis() {
        UUID parent = UUID.randomUUID();
        List<FoodCategoryView> tree = List.of(new FoodCategoryView(parent, null, "DAIRY", "Dairy", "dairy", 1,
                List.of(new FoodCategoryView(UUID.randomUUID(), parent, "YOGURT", "Yogurt", "yogurt", 1, List.of()))));

        assertThat(roundTrip(CatalogueCacheConfig.FOOD_CATEGORIES_CACHE, tree)).isEqualTo(tree);
    }

    @Test
    void entriesExpireAfterTheConfiguredTtlAndCarryNoClassNames() {
        RedisCacheConfiguration configuration = cacheConfiguration(CatalogueCacheConfig.FOOD_SEARCH_CACHE);
        ByteBuffer stored = configuration.getValueSerializationPair()
                .write(new FoodSearchPage(List.of(), 0, 20, 0, true));

        assertThat(configuration.getTtlFunction().getTimeToLive(new Object(), null)).isEqualTo(Duration.ofHours(1));
        assertThat(StandardCharsets.UTF_8.decode(stored).toString()).doesNotContain("@class");
    }

    private Object roundTrip(String cacheName, Object value) {
        RedisCacheConfiguration configuration = cacheConfiguration(cacheName);
        ByteBuffer stored = configuration.getValueSerializationPair().write(value);
        return configuration.getValueSerializationPair().read(stored);
    }

    private RedisCacheConfiguration cacheConfiguration(String cacheName) {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager.builder(connectionFactory);
        new CatalogueCacheConfig()
                .catalogueRedisCacheManagerBuilderCustomizer(objectMapper, connectionFactory, Duration.ofHours(1))
                .customize(builder);
        return builder.getCacheConfigurationFor(cacheName).orElseThrow();
    }
}
