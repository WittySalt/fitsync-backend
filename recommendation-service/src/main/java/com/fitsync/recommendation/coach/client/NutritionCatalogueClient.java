package com.fitsync.recommendation.coach.client;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fitsync.recommendation.coach.config.CoachProperties;
import com.fitsync.recommendation.coach.reading.NutritionInsight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Reads the food catalogue and the page insight from nutrition-service.
 *
 * <p>The search endpoint has no macro filter, so a caller wanting foods that fit a protein or
 * calorie budget over-fetches and narrows here. That is a known gap rather than an oversight: a
 * {@code minProteinPer100g} parameter belongs in nutrition-service, which owns the data, and is
 * recorded as such in the platform design.
 */
@Component
public class NutritionCatalogueClient extends CatalogueClient {

    private static final Logger LOG = LoggerFactory.getLogger(NutritionCatalogueClient.class);

    private static final String FOOD_SEARCH = "/api/v1/nutrition/foods/search";
    private static final String PAGE_INSIGHT = "/api/v1/nutrition/insights/page";

    // A reader is immutable, which a shared ObjectMapper is not.
    private final ObjectReader insightReader;

    public NutritionCatalogueClient(@Qualifier("coachRestClientBuilder") RestClient.Builder builder,
                                      CoachProperties properties,
                                      ObjectMapper objectMapper) {
        super(builder, properties.nutritionBaseUrl(), "nutrition");
        this.insightReader = objectMapper.readerFor(NutritionInsight.class);
    }

    public String searchFoods(String bearerToken, String query, int size) {
        Map<String, Object> params = params();
        params.put("q", query);
        params.put("page", 0);
        params.put("size", size);
        return get(FOOD_SEARCH, params, bearerToken);
    }

    /**
     * The pre-computed nutrition facts a nutrition-page reading is built from. Null covers both
     * "the service could not be reached" and "the body was not valid JSON for this contract", which
     * a caller treats alike: the upstream is unavailable.
     */
    public NutritionInsight pageInsight(String bearerToken, String timezone) {
        Map<String, Object> params = params();
        params.put("timezone", timezone);
        String body = get(PAGE_INSIGHT, params, bearerToken);
        if (body == null) {
            return null;
        }
        try {
            return insightReader.readValue(body);
        } catch (JsonProcessingException ex) {
            // The parser's own message can quote the body, so it is left out of the log.
            LOG.warn("event=nutrition_insight_unreadable");
            return null;
        }
    }
}
