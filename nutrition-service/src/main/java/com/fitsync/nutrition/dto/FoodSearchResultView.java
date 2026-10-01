package com.fitsync.nutrition.dto;

import java.util.UUID;

import com.fitsync.nutrition.domain.FoodCatalogScope;
import com.fitsync.nutrition.domain.FoodCatalogSource;

public record FoodSearchResultView(
        UUID foodId,
        String displayName,
        String brandName,
        String categoryCode,
        String categoryName,
        FoodCatalogScope scope,
        FoodCatalogSource source,
        boolean verified,
        Integer calories,
        String basisLabel,
        String primaryBarcode,
        String foodGroup,
        boolean countsTowardWater) {

    /** A result from a source that knows neither the plate group nor the water content, such as Open Food Facts. */
    public FoodSearchResultView(UUID foodId, String displayName, String brandName, String categoryCode,
                                String categoryName, FoodCatalogScope scope, FoodCatalogSource source,
                                boolean verified, Integer calories, String basisLabel, String primaryBarcode) {
        this(foodId, displayName, brandName, categoryCode, categoryName, scope, source, verified, calories,
                basisLabel, primaryBarcode, null, false);
    }
}
