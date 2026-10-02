package com.fitsync.recommendation.coach.tool;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fitsync.recommendation.coach.client.NutritionCatalogueClient;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import org.springframework.stereotype.Component;

/**
 * One recipe in full: what one serving holds, the vitamins and minerals it gives most of, the ingredients
 * with their grams, and the tags and diet flags.
 *
 * <p>The recipe is read with the caller's own credential, so a recipe the person may not see answers as
 * unavailable. Every figure is written as nutrition-service reports it, rounded the way the other tools round,
 * and each share is already a percentage, because the answer check accepts only numbers that appear in a tool
 * result. Recipe and ingredient names are text anyone may have typed, so they travel only inside the JSON
 * result, trimmed and cut short, and never in the system prompt.
 */
@Component
public class ReadRecipeTool implements CoachTool {

    static final int MAX_INGREDIENTS = 40;
    static final int TOP_NUTRIENTS = 3;

    private final NutritionCatalogueClient client;
    // A reader and a writer are immutable, which a shared ObjectMapper is not.
    private final ObjectReader reader;
    private final ObjectWriter writer;

    public ReadRecipeTool(NutritionCatalogueClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.reader = objectMapper.reader();
        this.writer = objectMapper.writer();
    }

    @Override
    public String name() {
        return "readRecipe";
    }

    @Override
    public ToolClass toolClass() {
        return ToolClass.READ;
    }

    @Override
    public String promptSignature() {
        return """
                readRecipe(recipeId)
                  recipeId: the id of a recipe, copied from a searchRecipes line.
                  returns JSON with the name, servings, one serving's calories, protein, carbohydrate,
                  fat, fibre, sugar and sodium, the three vitamins or minerals it gives the most of
                  with their share of the daily amount, the ingredients with their grams, and the tags
                  and diet flags. Every number here is already calculated, so quote it and never add
                  it up yourself.""";
    }

    @Override
    public ToolResult invoke(CoachCallContext context, JsonNode args) {
        String requested = args.path("recipeId").asText(null);
        if (requested == null || requested.isBlank()) {
            throw new IllegalArgumentException("readRecipe needs a recipeId from a searchRecipes line");
        }
        UUID recipeId;
        try {
            recipeId = UUID.fromString(requested.strip());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "readRecipe needs the recipeId exactly as a searchRecipes line gives it");
        }
        String body = client.recipe(context.bearerToken(), recipeId);
        if (body == null) {
            return unavailable();
        }
        try {
            JsonNode recipe = reader.readTree(body);
            if (!recipe.isObject() || !recipe.path("name").isTextual()) {
                return unavailable();
            }
            String name = ReadMealsOnDayTool.foodName(recipe.path("name").asText());
            return ToolResult.of("recipe " + name, writer.writeValueAsString(project(recipe, name)));
        } catch (JsonProcessingException ex) {
            return unavailable();
        }
    }

    private static Map<String, Object> project(JsonNode recipe, String name) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        String servings = DiaryFigures.number(recipe.path("servings"));
        if (servings != null) {
            result.put("servings", servings);
        }
        JsonNode perServing = recipe.path("perServing");
        Map<String, Object> figures = new LinkedHashMap<>();
        if (perServing.path("kcal").isNumber()) {
            figures.put("calories", Math.round(perServing.path("kcal").asDouble()));
        }
        put(figures, "proteinGrams", perServing.path("proteinGrams"));
        put(figures, "carbohydrateGrams", perServing.path("carbsGrams"));
        put(figures, "fatGrams", perServing.path("fatGrams"));
        put(figures, "fibreGrams", perServing.path("fiberGrams"));
        put(figures, "sugarGrams", perServing.path("sugarGrams"));
        put(figures, "sodiumMg", perServing.path("sodiumMg"));
        result.put("perServing", figures);
        List<String> top = topNutrients(perServing.path("nutrients"));
        if (!top.isEmpty()) {
            result.put("topVitaminsAndMinerals", top);
        }
        result.put("ingredients", ingredients(recipe.path("ingredients")));
        result.put("tags", texts(recipe.path("tags")));
        result.put("dietFlags", texts(recipe.path("dietFlags")));
        return result;
    }

    /** The vitamins and minerals with the largest share of their daily amount in one serving, largest first. */
    private static List<String> topNutrients(JsonNode nutrients) {
        List<JsonNode> candidates = new ArrayList<>();
        if (nutrients.isArray()) {
            for (JsonNode nutrient : nutrients) {
                if (nutrient.path("share").isNumber() && nutrient.path("amount").isNumber()) {
                    candidates.add(nutrient);
                }
            }
        }
        candidates.sort(Comparator.comparingDouble((JsonNode node) -> node.path("share").asDouble()).reversed());
        List<String> lines = new ArrayList<>();
        for (JsonNode nutrient : candidates.subList(0, Math.min(TOP_NUTRIENTS, candidates.size()))) {
            String unit = nutrient.path("unit").isTextual() && !nutrient.path("unit").asText().isBlank()
                    ? " " + nutrient.path("unit").asText().strip() : "";
            String label = nutrient.path("displayName").asText(nutrient.path("code").asText(""));
            lines.add(ReadMealsOnDayTool.foodName(label) + " " + DiaryFigures.percent(nutrient.path("share"))
                    + "% of the daily amount (" + DiaryFigures.number(nutrient.path("amount")) + unit + ")");
        }
        return lines;
    }

    private static List<String> ingredients(JsonNode ingredients) {
        List<String> lines = new ArrayList<>();
        if (!ingredients.isArray()) {
            return lines;
        }
        for (JsonNode ingredient : ingredients) {
            if (lines.size() >= MAX_INGREDIENTS) {
                break;
            }
            String label = ingredient.path("name").asText(ingredient.path("foodName").asText(""));
            String name = ReadMealsOnDayTool.foodName(label);
            if (name.isEmpty()) {
                continue;
            }
            String grams = DiaryFigures.number(ingredient.path("grams"));
            lines.add(grams == null ? name : name + " " + grams + " g");
        }
        return lines;
    }

    private static List<String> texts(JsonNode values) {
        List<String> texts = new ArrayList<>();
        if (values.isArray()) {
            for (JsonNode value : values) {
                if (value.isTextual() && !value.asText().isBlank()) {
                    texts.add(ReadMealsOnDayTool.foodName(value.asText()));
                }
            }
        }
        return texts;
    }

    private static void put(Map<String, Object> into, String key, JsonNode value) {
        if (value.isNumber()) {
            BigDecimal rounded = value.decimalValue().setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
            into.put(key, rounded.scale() < 0 ? rounded.setScale(0) : rounded);
        }
    }

    private static ToolResult unavailable() {
        return ToolResult.of("recipe unavailable",
                "That recipe could not be read. It may not exist, or the recipe service could not be reached. "
                        + "Answer without it, or say so.");
    }
}
