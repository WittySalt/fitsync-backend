package com.fitsync.recommendation.coach.client;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

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
    private static final String DAILY_SUMMARY = "/api/v1/nutrition/summary/daily";
    private static final String MEALS = "/api/v1/nutrition/meals";
    private static final String NUTRIENTS_SUMMARY = "/api/v1/nutrition/nutrients/summary";
    private static final String WATER = "/api/v1/nutrition/water";
    private static final String RECIPES = "/api/v1/nutrition/recipes";

    // A reader is immutable, which a shared ObjectMapper is not.
    private final ObjectReader insightReader;

    public NutritionCatalogueClient(@Qualifier("coachRestClientBuilder") RestClient.Builder builder,
                                      CoachProperties properties,
                                      ObjectMapper objectMapper) {
        super(builder, properties.apiBaseUrl(), "nutrition");
        this.insightReader = objectMapper.readerFor(NutritionInsight.class);
    }

    public String searchFoods(String bearerToken, String query, int size) {
        Map<String, Object> params = params();
        params.put("q", query);
        params.put("page", 0);
        params.put("size", size);
        return get(FOOD_SEARCH, params, bearerToken);
    }

    /** The totals and targets of one local day, as nutrition-service reports them. */
    public String dailySummary(String bearerToken, LocalDate date, String timezone) {
        Map<String, Object> params = params();
        params.put("date", date.toString());
        return get(DAILY_SUMMARY, params, bearerToken, timezone);
    }

    /**
     * The vitamins and minerals over a run of local days, each against its daily reference. Null
     * when nutrition-service cannot be reached, and also when it predates this endpoint, which a
     * caller treats alike: the figures are an addition and the answer goes on without them.
     */
    public String nutrientsSummary(String bearerToken, LocalDate from, LocalDate to, String timezone) {
        Map<String, Object> params = params();
        params.put("from", from.toString());
        params.put("to", to.toString());
        return get(NUTRIENTS_SUMMARY, params, bearerToken, timezone);
    }

    /** The plain water logged on one local day, and the water that came from drinks. */
    public String water(String bearerToken, LocalDate date, String timezone) {
        Map<String, Object> params = params();
        params.put("date", date.toString());
        return get(WATER, params, bearerToken, timezone);
    }

    /** One recipe in full, as the caller may see it. Null when it is not visible to the caller or the service cannot be reached. */
    public String recipe(String bearerToken, UUID recipeId) {
        return get(RECIPES + "/" + recipeId, params(), bearerToken);
    }

    /**
     * One page of the shared recipe library, best fit for today first, optionally narrowed by a name text, a
     * diet and a card tag. The endpoint has no calorie or protein filter, so a caller that wants one narrows
     * the cards itself.
     */
    public String searchRecipes(String bearerToken, String query, String diet, String tag, String timezone,
                                int page, int size) {
        Map<String, Object> params = params();
        params.put("scope", "library");
        params.put("sort", "fit");
        params.put("q", query);
        params.put("diet", diet);
        params.put("tag", tag);
        params.put("page", page);
        params.put("size", size);
        return get(RECIPES, params, bearerToken, timezone);
    }

    /** The caller's meals between two instants, both ends included. */
    public String meals(String bearerToken, Instant from, Instant to) {
        Map<String, Object> params = params();
        params.put("from", from.toString());
        params.put("to", to.toString());
        return get(MEALS, params, bearerToken);
    }

    /**
     * The pre-computed nutrition facts a nutrition-page reading is built from. Null covers both
     * "the service could not be reached" and "the body was not valid JSON for this contract", which
     * a caller treats alike: the upstream is unavailable.
     */
    public NutritionInsight pageInsight(String bearerToken, String timezone) {
        Map<String, Object> params = params();
        String body = get(PAGE_INSIGHT, params, bearerToken, timezone);
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
