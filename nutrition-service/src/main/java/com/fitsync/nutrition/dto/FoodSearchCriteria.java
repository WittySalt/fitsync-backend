package com.fitsync.nutrition.dto;

import java.util.UUID;

import com.fitsync.nutrition.domain.FoodCatalogScope;

/**
 * Optional filters + paging for {@code GET /foods/search}. Any null field is an unset filter.
 * When {@code categoryId} is set, {@code includeSubcategories} chooses subtree vs exact-category
 * matching. {@code page} is 0-based; {@code size} is clamped to a service maximum. {@code foodGroup} keeps
 * only foods of one plate group such as DRINKS.
 */
public record FoodSearchCriteria(
        String query,
        UUID categoryId,
        boolean includeSubcategories,
        UUID brandId,
        Boolean verified,
        FoodCatalogScope scope,
        int page,
        int size,
        String foodGroup) {
}
