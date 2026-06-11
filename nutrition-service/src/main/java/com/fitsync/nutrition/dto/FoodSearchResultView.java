package com.fitsync.nutrition.dto;

import java.util.UUID;

import com.fitsync.nutrition.domain.FoodCatalogScope;
import com.fitsync.nutrition.domain.FoodCatalogSource;

public record FoodSearchResultView(
        UUID foodId,
        String displayName,
        String brandName,
        String categoryCode,
        FoodCatalogScope scope,
        FoodCatalogSource source,
        boolean verified,
        Integer calories,
        String basisLabel,
        String primaryBarcode) {
}
