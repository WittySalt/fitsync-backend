package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.domain.FoodCatalogScope;
import com.fitsync.nutrition.domain.FoodCreationMode;
import com.fitsync.nutrition.domain.FoodCatalogSource;

public record FoodDetailView(
        UUID foodId,
        String displayName,
        String normalizedName,
        String brandName,
        String brandLogoUrl,
        String categoryCode,
        FoodCatalogScope scope,
        UUID baseFoodId,
        FoodCreationMode creationMode,
        FoodCatalogSource source,
        boolean verified,
        BigDecimal densityGramsPerMl,
        Integer calories,
        BigDecimal proteinGrams,
        BigDecimal carbsGrams,
        BigDecimal fatGrams,
        BigDecimal saturatedFatGrams,
        BigDecimal transFatGrams,
        BigDecimal fiberGrams,
        BigDecimal sugarGrams,
        BigDecimal sodiumMg,
        BigDecimal potassiumMg,
        BigDecimal cholesterolMg,
        BigDecimal basisQuantity,
        String basisUnit,
        String primaryBarcode,
        Integer novaGroup,
        String nutriScore,
        String sourceDataset,
        List<FoodServingView> servings,
        List<NutrientDetailView> nutrientDetails,
        String foodGroup,
        boolean countsTowardWater,
        BigDecimal waterGrams,
        BigDecimal alcoholGrams) {

    /** A food from a source that does not know its plate group or water content, such as Open Food Facts. */
    public FoodDetailView(UUID foodId, String displayName, String normalizedName, String brandName,
                          String categoryCode, FoodCatalogScope scope, UUID baseFoodId,
                          FoodCreationMode creationMode, FoodCatalogSource source, boolean verified,
                          BigDecimal densityGramsPerMl, Integer calories, BigDecimal proteinGrams,
                          BigDecimal carbsGrams, BigDecimal fatGrams, BigDecimal saturatedFatGrams,
                          BigDecimal transFatGrams, BigDecimal fiberGrams, BigDecimal sugarGrams,
                          BigDecimal sodiumMg, BigDecimal potassiumMg, BigDecimal cholesterolMg,
                          BigDecimal basisQuantity, String basisUnit, String primaryBarcode, Integer novaGroup,
                          String nutriScore, String sourceDataset, List<FoodServingView> servings,
                          List<NutrientDetailView> nutrientDetails) {
        this(foodId, displayName, normalizedName, brandName, null, categoryCode, scope, baseFoodId, creationMode,
                source, verified, densityGramsPerMl, calories, proteinGrams, carbsGrams, fatGrams, saturatedFatGrams,
                transFatGrams, fiberGrams, sugarGrams, sodiumMg, potassiumMg, cholesterolMg, basisQuantity,
                basisUnit, primaryBarcode, novaGroup, nutriScore, sourceDataset, servings, nutrientDetails,
                null, false, null, null);
    }

    public FoodDetailView {
        servings = servings == null ? List.of() : List.copyOf(servings);
        nutrientDetails = nutrientDetails == null ? List.of() : List.copyOf(nutrientDetails);
    }

    @Override
    public List<FoodServingView> servings() {
        return List.copyOf(servings);
    }

    @Override
    public List<NutrientDetailView> nutrientDetails() {
        return List.copyOf(nutrientDetails);
    }
}
