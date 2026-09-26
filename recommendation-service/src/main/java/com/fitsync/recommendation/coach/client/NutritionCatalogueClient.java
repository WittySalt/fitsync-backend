package com.fitsync.recommendation.coach.client;

import java.util.Map;

import com.fitsync.recommendation.coach.config.CoachProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Reads the food catalogue from nutrition-service.
 *
 * <p>The search endpoint has no macro filter, so a caller wanting foods that fit a protein or
 * calorie budget over-fetches and narrows here. That is a known gap rather than an oversight: a
 * {@code minProteinPer100g} parameter belongs in nutrition-service, which owns the data, and is
 * recorded as such in the platform design.
 */
@Component
public class NutritionCatalogueClient extends CatalogueClient {

    private static final String FOOD_SEARCH = "/api/v1/nutrition/foods/search";

    public NutritionCatalogueClient(@Qualifier("coachRestClientBuilder") RestClient.Builder builder,
                                      CoachProperties properties) {
        super(builder, properties.nutritionBaseUrl(), "nutrition");
    }

    public String searchFoods(String bearerToken, String query, int size) {
        Map<String, Object> params = params();
        params.put("q", query);
        params.put("page", 0);
        params.put("size", size);
        return get(FOOD_SEARCH, params, bearerToken);
    }
}
