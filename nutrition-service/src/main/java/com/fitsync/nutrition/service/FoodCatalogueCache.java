package com.fitsync.nutrition.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;

import com.fitsync.nutrition.config.CatalogueCacheConfig;
import com.fitsync.nutrition.dto.FoodSearchCriteria;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Cache-aside for searches of the shared food catalogue, which is the same for every user.
 *
 * <p>Only a search that can contain catalogue foods alone comes here: the loader always searches as
 * {@link #CATALOGUE_ONLY_OWNER}, an owner no food has, so a cached page can never hold anyone's own food. A
 * caller with foods of their own is answered from the database instead. The key names every search parameter
 * and the day, since a food can take effect or expire on a given date.
 */
@Component
public class FoodCatalogueCache {

    /** Owns no food, so the accessible-foods search returns the catalogue only. */
    static final UUID CATALOGUE_ONLY_OWNER = new UUID(0L, 0L);

    /** Longer text is answered from the database, so a user cannot write arbitrarily long keys. */
    static final int MAX_CACHED_QUERY_LENGTH = 100;

    @Cacheable(cacheNames = CatalogueCacheConfig.FOOD_SEARCH_CACHE, key = "#key", unless = "!#result.complete()")
    public FoodSearchPage search(String key, Supplier<FoodSearchPage> loader) {
        return loader.get();
    }

    static boolean isCacheable(FoodSearchCriteria criteria, String normalizedQuery) {
        return (normalizedQuery == null || normalizedQuery.length() <= MAX_CACHED_QUERY_LENGTH)
                && (criteria.foodGroup() == null || criteria.foodGroup().length() <= MAX_CACHED_QUERY_LENGTH);
    }

    /**
     * Every parameter of the search, as it was sent. Text is URL-encoded so that no value can run into the next
     * one. The search text keeps its case, because the external food source is asked with the text as typed.
     */
    static String key(FoodSearchCriteria criteria, String normalizedQuery, LocalDate onDate) {
        return String.join("|",
                "v1",
                "day=" + onDate,
                "q=" + (normalizedQuery == null ? "" : URLEncoder.encode(normalizedQuery, StandardCharsets.UTF_8)),
                "category=" + criteria.categoryId(),
                "subcategories=" + criteria.includeSubcategories(),
                "brand=" + criteria.brandId(),
                "verified=" + criteria.verified(),
                "scope=" + criteria.scope(),
                "group=" + (criteria.foodGroup() == null
                        ? "" : URLEncoder.encode(criteria.foodGroup(), StandardCharsets.UTF_8)),
                "page=" + criteria.page(),
                "size=" + criteria.size());
    }
}
