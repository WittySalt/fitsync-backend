package com.fitsync.recommendation.coach.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fitsync.recommendation.coach.client.NutritionCatalogueClient;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import org.springframework.stereotype.Component;

/**
 * What nutrition-service suggests the person cooks tonight, with the facts behind it, so that the coach can
 * answer "why this one" from the same figures the Cook for tonight card shows.
 *
 * <p>The answer is the endpoint's own payload: the state of the day, the facts and up to three suggestions each
 * with the reason that service wrote. Nothing is ranked or worked out here. Recipe names are text anyone may have
 * typed, so they are trimmed and cut short, and the image address is left out because the model has no use for it.
 */
@Component
public class ReadRecipeSuggestionsTool implements CoachTool {

    static final int MAX_LIMIT = 3;

    private final NutritionCatalogueClient client;
    private final UserCalendar calendar;
    // A reader and a writer are immutable, which a shared ObjectMapper is not.
    private final ObjectReader reader;
    private final ObjectWriter writer;

    public ReadRecipeSuggestionsTool(NutritionCatalogueClient client, UserCalendar calendar,
                                     ObjectMapper objectMapper) {
        this.client = client;
        this.calendar = calendar;
        this.reader = objectMapper.reader();
        this.writer = objectMapper.writer();
    }

    @Override
    public String name() {
        return "readRecipeSuggestions";
    }

    @Override
    public ToolClass toolClass() {
        return ToolClass.READ;
    }

    @Override
    public String promptSignature() {
        return """
                readRecipeSuggestions(limit)
                  limit: optional, 1 to 3 suggestions, three when left out.
                  returns JSON with the state of today (FITS, NOTHING_LOGGED, PAST_TARGET, PROTEIN_ONLY or
                  DINNER_LOGGED), the facts it was worked out from (calories left, protein still short, the
                  lowest vitamin or mineral, the usual dinner time, calories eaten and the target) and the
                  recipes suggested for tonight, each with its calories and protein for one serving and
                  one sentence saying why it was chosen. Use it to answer why a recipe was suggested, and
                  quote the sentence and the figures rather than working them out yourself. Suggestions
                  are empty once dinner is logged.""";
    }

    @Override
    public ToolResult invoke(CoachCallContext context, JsonNode args) {
        int limit = MAX_LIMIT;
        JsonNode requested = args.path("limit");
        if (!requested.isMissingNode() && !requested.isNull()) {
            if (!requested.canConvertToInt() || requested.asInt() < 1 || requested.asInt() > MAX_LIMIT) {
                throw new IllegalArgumentException("readRecipeSuggestions limit must be between 1 and " + MAX_LIMIT);
            }
            limit = requested.asInt();
        }
        String body = client.recipeSuggestions(context.bearerToken(), limit, calendar.zone(context.userId()).getId());
        if (body == null) {
            return unavailable();
        }
        try {
            JsonNode payload = reader.readTree(body);
            if (!payload.isObject() || !payload.path("state").isTextual() || !payload.path("suggestions").isArray()) {
                return unavailable();
            }
            ObjectNode result = payload.deepCopy();
            ArrayNode suggestions = (ArrayNode) result.path("suggestions");
            for (JsonNode suggestion : suggestions) {
                ObjectNode entry = (ObjectNode) suggestion;
                entry.remove("imageUrl");
                if (entry.path("name").isTextual()) {
                    entry.put("name", ReadMealsOnDayTool.foodName(entry.path("name").asText()));
                }
            }
            return ToolResult.of("recipe suggestions " + payload.path("state").asText(),
                    writer.writeValueAsString(result));
        } catch (JsonProcessingException ex) {
            return unavailable();
        }
    }

    private static ToolResult unavailable() {
        return ToolResult.of("recipe suggestions unavailable",
                "The recipe suggestions could not be read. Answer without them, or say so.");
    }
}
