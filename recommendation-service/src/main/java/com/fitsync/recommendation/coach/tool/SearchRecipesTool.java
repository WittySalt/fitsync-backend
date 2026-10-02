package com.fitsync.recommendation.coach.tool;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fitsync.recommendation.coach.client.NutritionCatalogueClient;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import org.springframework.stereotype.Component;

/**
 * Recipe retrieval from the shared library, the only source the coach may suggest a recipe from.
 *
 * <p>The library endpoint filters by name, diet and tag but has no calorie or protein filter, so the cards are
 * read page by page, best fit for today first, and narrowed here until eight recipes are found or the pages run
 * out. The lines carry the id verbatim so a later readRecipe call names a real recipe rather than one the model
 * invented. Names are text that came from outside, so they are cut short and cleaned of the line separator.
 */
@Component
public class SearchRecipesTool implements CoachTool {

    static final int MAX_LINES = 8;
    static final int PAGE_SIZE = 50;
    static final int MAX_PAGES = 6;
    static final int MAX_QUERY_LENGTH = 80;
    private static final Set<String> DIETS = Set.of("VEGETARIAN", "VEGAN");
    private static final Set<String> TAGS = Set.of("HIGH_PROTEIN", "HIGH_FIBRE", "HIGH_SUGAR", "HIGH_SALT");

    private final NutritionCatalogueClient client;
    private final UserCalendar calendar;
    // A reader is immutable, which a shared ObjectMapper is not.
    private final ObjectReader reader;

    public SearchRecipesTool(NutritionCatalogueClient client, UserCalendar calendar, ObjectMapper objectMapper) {
        this.client = client;
        this.calendar = calendar;
        this.reader = objectMapper.reader();
    }

    @Override
    public String name() {
        return "searchRecipes";
    }

    @Override
    public ToolClass toolClass() {
        return ToolClass.READ;
    }

    @Override
    public String promptSignature() {
        return """
                searchRecipes(query, maxKcal, minProtein, diet, tag)
                  Every argument is optional. query: part of a recipe name, one word such as "chicken".
                  maxKcal: the most calories in one serving. minProtein: the least protein in one serving,
                  in grams. diet: VEGETARIAN or VEGAN. tag: HIGH_PROTEIN, HIGH_FIBRE, HIGH_SUGAR or
                  HIGH_SALT.
                  returns up to 8 lines of "id | name | kcal | protein | fat | tags", per serving, the
                  best fit for what is left of today first.
                  You may suggest a recipe only when it is on a line returned by searchRecipes. Never
                  name a recipe from memory, and say that no recipe was found when the lines are empty.""";
    }

    @Override
    public ToolResult invoke(CoachCallContext context, JsonNode args) {
        String query = text(args, "query");
        if (query != null && query.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("searchRecipes query is at most " + MAX_QUERY_LENGTH + " characters");
        }
        BigDecimal maxKcal = limit(args, "maxKcal", false);
        BigDecimal minProtein = limit(args, "minProtein", true);
        String diet = oneOf(args, "diet", DIETS);
        String tag = oneOf(args, "tag", TAGS);
        String timezone = calendar.zone(context.userId()).getId();

        List<String> lines = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES && lines.size() < MAX_LINES; page++) {
            String body = client.searchRecipes(context.bearerToken(), query, diet, tag, timezone, page, PAGE_SIZE);
            if (body == null) {
                if (page == 0) {
                    return ToolResult.of("recipes unavailable",
                            "The recipe library could not be reached. Answer without it, or say so.");
                }
                break;
            }
            try {
                JsonNode cards = reader.readTree(body);
                for (JsonNode card : cards.path("content")) {
                    if (lines.size() < MAX_LINES && fits(card, maxKcal, minProtein)) {
                        lines.add(line(card));
                    }
                }
                if (cards.path("last").asBoolean(true)) {
                    break;
                }
            } catch (JsonProcessingException ex) {
                // A library that answered with something unparseable is a missing result, not a failed turn.
                break;
            }
        }
        if (lines.isEmpty()) {
            return ToolResult.of("0 recipes",
                    "No recipe in the library matched. Say that no recipe was found, or try fewer limits.");
        }
        return ToolResult.of(lines.size() + " recipes", String.join("\n", lines));
    }

    private static boolean fits(JsonNode card, BigDecimal maxKcal, BigDecimal minProtein) {
        if (!card.path("id").isTextual() || !card.path("kcalPerServing").isNumber()) {
            return false;
        }
        if (maxKcal != null && card.path("kcalPerServing").decimalValue().compareTo(maxKcal) > 0) {
            return false;
        }
        return minProtein == null || (card.path("proteinGramsPerServing").isNumber()
                && card.path("proteinGramsPerServing").decimalValue().compareTo(minProtein) >= 0);
    }

    private static String line(JsonNode card) {
        List<String> tags = new ArrayList<>();
        for (JsonNode tag : card.path("tags")) {
            if (tag.isTextual()) {
                tags.add(clean(tag.asText()));
            }
        }
        String protein = DiaryFigures.number(card.path("proteinGramsPerServing"));
        String fat = DiaryFigures.number(card.path("fatGramsPerServing"));
        return "%s | %s | %d kcal | %s | %s | %s".formatted(
                clean(card.path("id").asText()),
                clean(card.path("name").asText("")),
                Math.round(card.path("kcalPerServing").asDouble()),
                protein == null ? "protein unknown" : protein + " g protein",
                fat == null ? "fat unknown" : fat + " g fat",
                tags.isEmpty() ? "no tags" : String.join(", ", tags));
    }

    /** A name cut short, with the separator and the line breaks that would split a line taken out. */
    private static String clean(String raw) {
        return ReadMealsOnDayTool.foodName(raw.replace('|', '/').replaceAll("\\s+", " "));
    }

    private static String text(JsonNode args, String key) {
        String value = args.path(key).asText(null);
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static BigDecimal limit(JsonNode args, String key, boolean allowZero) {
        JsonNode node = args.path(key);
        if (node.isMissingNode() || node.isNull() || (node.isTextual() && node.asText().isBlank())) {
            return null;
        }
        BigDecimal value;
        try {
            value = node.isNumber() ? node.decimalValue() : new BigDecimal(node.asText().strip());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("searchRecipes " + key + " must be a number");
        }
        if (value.signum() < 0 || (!allowZero && value.signum() == 0)) {
            throw new IllegalArgumentException("searchRecipes " + key + " must be greater than zero");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String oneOf(JsonNode args, String key, Set<String> allowed) {
        String value = text(args, key);
        if (value == null) {
            return null;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if (!allowed.contains(upper)) {
            throw new IllegalArgumentException("searchRecipes " + key + " must be one of "
                    + String.join(", ", allowed.stream().sorted().toList()));
        }
        return upper;
    }
}
