package com.fitsync.recommendation.coach.tool;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.client.NutritionCatalogueClient;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import org.springframework.stereotype.Component;

/**
 * Food catalogue retrieval over 7,490 rows.
 *
 * <p>Projected to name, energy and category. Protein is missing on purpose rather than by omission:
 * the search endpoint nutrition-service exposes does not carry it, and a column that reads "?" on
 * every row teaches the model that a fact it needs is simply unavailable, which is worse than not
 * offering it. Ranking foods by protein needs a macro filter in the service that owns the data, and
 * that gap is recorded against nutrition-service in the platform design.
 *
 * <p>The identifier is kept verbatim so a draft can reference a real catalogue row rather than a
 * food the model invented.
 */
@Component
public class SearchFoodsTool implements CoachTool {

    private static final int PAGE_SIZE = 20;

    private final NutritionCatalogueClient client;
    private final ObjectMapper objectMapper;

    public SearchFoodsTool(NutritionCatalogueClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "searchFoods";
    }

    @Override
    public ToolClass toolClass() {
        return ToolClass.READ;
    }

    @Override
    public String promptSignature() {
        return """
                searchFoods(query)
                  query: one food name, for example "salmon" or "rolled oats". Search one food at
                         a time; a phrase like "high protein dinner" matches nothing.
                  returns up to 20 lines of "id | name | energy | category\"""";
    }

    @Override
    public ToolResult invoke(CoachCallContext context, JsonNode args) {
        String query = args.path("query").asText(null);
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("searchFoods needs a query");
        }

        String body = client.searchFoods(context.bearerToken(), query.strip(), PAGE_SIZE);
        if (body == null) {
            return ToolResult.of("catalogue unavailable",
                    "The food catalogue could not be reached. Answer without it, or say so.");
        }

        List<String> rows = project(body);
        if (rows.isEmpty()) {
            return ToolResult.of("0 candidates",
                    "No food matched \"" + query.strip() + "\". Try a simpler or more common name.");
        }
        return ToolResult.of(rows.size() + " candidates", String.join("\n", rows));
    }

    private List<String> project(String body) {
        List<String> rows = new ArrayList<>();
        try {
            JsonNode content = objectMapper.readTree(body).path("content");
            for (JsonNode item : content) {
                rows.add("%s | %s | %s kcal per %s | %s".formatted(
                        item.path("foodId").asText(),
                        item.path("displayName").asText(),
                        item.path("calories").asText("?"),
                        item.path("basisLabel").asText("100 g"),
                        item.path("categoryName").asText("")));
            }
        } catch (JsonProcessingException ex) {
            // A catalogue that answered with something unparseable is a missing result, not a
            // failed turn: the model is told there were no candidates and works around it.
            return List.of();
        }
        return rows;
    }
}
