package com.fitsync.nutrition;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.fitsync.nutrition.config.CatalogueCacheConfig;
import com.fitsync.nutrition.domain.FoodCatalogScope;
import com.fitsync.nutrition.domain.FoodCatalogSource;
import com.fitsync.nutrition.domain.FoodCreationMode;
import com.fitsync.nutrition.dto.FoodDetailView;
import com.fitsync.nutrition.dto.FoodSearchCriteria;
import com.fitsync.nutrition.dto.FoodSearchResultView;
import com.fitsync.nutrition.external.ExternalFoodSource;
import com.fitsync.nutrition.repository.FoodBarcodeRepository;
import com.fitsync.nutrition.repository.FoodBrandRepository;
import com.fitsync.nutrition.repository.FoodCatalogItemRepository;
import com.fitsync.nutrition.repository.FoodNutrientDetailRepository;
import com.fitsync.nutrition.repository.FoodNutrientProfileRepository;
import com.fitsync.nutrition.repository.FoodServingRepository;
import com.fitsync.nutrition.security.CurrentUserPrincipal;
import com.fitsync.nutrition.service.NutritionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The food catalogue search cache with an in-memory store standing in for Redis; the Redis wiring itself is
 * covered by {@code CatalogueCacheSerializationTests}. A plain text search on the first page that finds few
 * local foods also asks the external food source, so the number of external searches shows whether a search
 * ran or was answered from the cache. A database of its own, so nothing here disturbs the other tests.
 */
@SpringBootTest(properties = {
        "spring.cache.type=simple",
        "spring.datasource.url=jdbc:h2:mem:nutrition_catalogue_cache;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS nutrition;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@ActiveProfiles("test")
class FoodCatalogueCacheTests {

    private static final String BARCODE = "5000000000017";

    @Autowired
    private NutritionService nutritionService;

    @Autowired
    private CountingExternalFoodSource externalFoodSource;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private FoodBrandRepository foodBrandRepository;

    @Autowired
    private FoodCatalogItemRepository foodCatalogItemRepository;

    @Autowired
    private FoodServingRepository foodServingRepository;

    @Autowired
    private FoodBarcodeRepository foodBarcodeRepository;

    @Autowired
    private FoodNutrientProfileRepository foodNutrientProfileRepository;

    @Autowired
    private FoodNutrientDetailRepository foodNutrientDetailRepository;

    @BeforeEach
    void reset() {
        externalFoodSource.reset();
        foodNutrientDetailRepository.deleteAll();
        foodBarcodeRepository.deleteAll();
        foodServingRepository.deleteAll();
        foodNutrientProfileRepository.deleteAll();
        foodCatalogItemRepository.deleteAll();
        foodBrandRepository.deleteAll();
        for (String name : List.of(CatalogueCacheConfig.FOOD_SEARCH_CACHE, CatalogueCacheConfig.FOOD_CATEGORIES_CACHE)) {
            Cache cache = cacheManager.getCache(name);
            assertThat(cache).isNotNull();
            cache.clear();
        }
    }

    @Test
    void theSecondIdenticalSearchIsAnsweredFromTheCacheForAnyUserWithoutOwnFoods() {
        Page<FoodSearchResultView> first = nutritionService.searchFoods(newUser(), criteria("rarefruit", 0, 20));
        Page<FoodSearchResultView> second = nutritionService.searchFoods(newUser(), criteria("rarefruit", 0, 20));

        assertThat(externalFoodSource.searches()).isEqualTo(1);
        assertThat(second.getContent()).isEqualTo(first.getContent());
        assertThat(second.getTotalElements()).isEqualTo(first.getTotalElements());
        assertThat(second.getPageable()).isEqualTo(first.getPageable());
    }

    @Test
    void aDifferentSearchMissesTheCache() {
        nutritionService.searchFoods(newUser(), criteria("rarefruit", 0, 20));
        nutritionService.searchFoods(newUser(), criteria("rarefruits", 0, 20));
        nutritionService.searchFoods(newUser(), criteria("rarefruit", 0, 10));
        nutritionService.searchFoods(newUser(), criteria("Rarefruit", 0, 20));

        assertThat(externalFoodSource.searches()).isEqualTo(4);
    }

    @Test
    void aPageMissingTheExternalResultsIsAnsweredButNotCached() {
        externalFoodSource.failSearches(true);

        nutritionService.searchFoods(newUser(), criteria("rarefruit", 0, 20));
        nutritionService.searchFoods(newUser(), criteria("rarefruit", 0, 20));

        assertThat(externalFoodSource.searches()).isEqualTo(2);
    }

    @Test
    void aBarcodeImportEvictsTheCachedSearches() {
        Page<FoodSearchResultView> before = nutritionService.searchFoods(newUser(), criteria("Cache Yogurt", 0, 20));
        externalFoodSource.addBarcodeResult(externalFood());

        nutritionService.importFoodByBarcode(newUser(), BARCODE);
        Page<FoodSearchResultView> after = nutritionService.searchFoods(newUser(), criteria("Cache Yogurt", 0, 20));

        assertThat(before.getContent()).isEmpty();
        assertThat(after.getContent()).extracting(FoodSearchResultView::displayName).contains("Cache Yogurt");
    }

    @Test
    void aUserWhoOwnsAFoodIsAnsweredFromTheDatabaseAndNeverSharesIt() {
        externalFoodSource.addBarcodeResult(externalFood());
        UUID foodId = nutritionService.importFoodByBarcode(newUser(), BARCODE).foodId();
        CurrentUserPrincipal owner = newUser();
        nutritionService.cloneFood(owner, foodId);
        externalFoodSource.reset();

        Page<FoodSearchResultView> ownersFirst = nutritionService.searchFoods(owner, criteria("Cache Yogurt", 0, 20));
        nutritionService.searchFoods(owner, criteria("Cache Yogurt", 0, 20));
        Page<FoodSearchResultView> someoneElses = nutritionService.searchFoods(newUser(), criteria("Cache Yogurt", 0, 20));

        assertThat(externalFoodSource.searches()).isEqualTo(3);
        assertThat(ownersFirst.getContent()).hasSize(2);
        assertThat(someoneElses.getContent()).hasSize(1);
        assertThat(someoneElses.getContent().get(0).foodId()).isEqualTo(foodId);
    }

    @Test
    void theCategoryTreeIsCached() {
        nutritionService.listCategories(newUser());

        assertThat(cacheManager.getCache(CatalogueCacheConfig.FOOD_CATEGORIES_CACHE).get("all")).isNotNull();
    }

    private static FoodSearchCriteria criteria(String query, int page, int size) {
        return new FoodSearchCriteria(query, null, true, null, null, null, page, size, null);
    }

    private static CurrentUserPrincipal newUser() {
        return new CurrentUserPrincipal(UUID.randomUUID(), "user@example.com", "User", "USER");
    }

    private static FoodDetailView externalFood() {
        String displayName = "Cache Yogurt";
        return new FoodDetailView(
                null,
                displayName,
                displayName.toLowerCase(Locale.ROOT),
                "Cache Dairy",
                "DAIRY",
                FoodCatalogScope.GLOBAL,
                null,
                FoodCreationMode.MANUAL,
                FoodCatalogSource.IMPORTED,
                false,
                null,
                59,
                new BigDecimal("10.30"),
                new BigDecimal("3.60"),
                new BigDecimal("0.40"),
                null,
                null,
                BigDecimal.ZERO,
                new BigDecimal("3.20"),
                new BigDecimal("36.00"),
                null,
                null,
                new BigDecimal("100.00"),
                "g",
                BARCODE,
                1,
                "A",
                "OFF",
                List.of(),
                List.of());
    }

    @TestConfiguration
    static class CountingExternalFoodSourceConfig {

        @Bean
        @Primary
        CountingExternalFoodSource countingExternalFoodSource() {
            return new CountingExternalFoodSource();
        }
    }

    static class CountingExternalFoodSource implements ExternalFoodSource {

        private final AtomicInteger searches = new AtomicInteger();
        private final AtomicBoolean failSearches = new AtomicBoolean();
        private volatile FoodDetailView barcodeResult;

        @Override
        public Optional<FoodDetailView> findByBarcode(String barcode) {
            return BARCODE.equals(barcode) ? Optional.ofNullable(barcodeResult) : Optional.empty();
        }

        @Override
        public List<FoodSearchResultView> search(String query, int limit) {
            searches.incrementAndGet();
            if (failSearches.get()) {
                throw new IllegalStateException("search unavailable");
            }
            return List.of();
        }

        void addBarcodeResult(FoodDetailView food) {
            barcodeResult = food;
        }

        void failSearches(boolean fail) {
            failSearches.set(fail);
        }

        int searches() {
            return searches.get();
        }

        void reset() {
            searches.set(0);
            failSearches.set(false);
            barcodeResult = null;
        }
    }
}
