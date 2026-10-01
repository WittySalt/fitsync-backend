package com.fitsync.recommendation.coach.tool;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fitsync.recommendation.coach.client.NutritionCatalogueClient;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import org.springframework.stereotype.Component;

/**
 * What the user ate on one day, read from nutrition-service.
 *
 * <p>The day's totals come from the same summary the nutrition page shows, and the meals come from
 * the meal log for the same local day, so the coach's figures agree with the page. Every number is
 * rounded here the way the computed facts are, whole kilocalories and grams to one decimal, and the
 * model is told to quote them rather than add them up.
 *
 * <p>Food names are text the user typed, so they travel only inside the JSON result the model reads
 * as data, trimmed and cut short. They are never placed in the system prompt.
 */
@Component
public class ReadMealsOnDayTool implements CoachTool {

    /** How far back a day may be asked for, counted from today in the user's timezone. */
    static final int MAX_DAYS_BACK = 60;

    static final int MAX_MEALS = 10;
    static final int MAX_FOODS_PER_MEAL = 12;
    static final int MAX_FOOD_NAME_LENGTH = 80;

    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final NutritionCatalogueClient client;
    private final UserCalendar calendar;
    // A reader and a writer are immutable, which a shared ObjectMapper is not.
    private final ObjectReader reader;
    private final ObjectWriter writer;

    public ReadMealsOnDayTool(NutritionCatalogueClient client, UserCalendar calendar, ObjectMapper objectMapper) {
        this.client = client;
        this.calendar = calendar;
        this.reader = objectMapper.reader();
        this.writer = objectMapper.writer();
    }

    @Override
    public String name() {
        return "readMealsOnDay";
    }

    @Override
    public ToolClass toolClass() {
        return ToolClass.READ;
    }

    @Override
    public String promptSignature() {
        return """
                readMealsOnDay(date)
                  date: one day as YYYY-MM-DD, today or up to 60 days back. Work out a date such as
                        yesterday from today's date, which is given above.
                  returns JSON with the day's totals (calories, protein, carbohydrate and fat, with
                  the targets when there are any) and the meals in time order: meal type, local time,
                  calories, protein and food names. Every number here is already calculated, so
                  quote it and never add it up yourself. When the service has them, a diary group adds
                  the day's fibre, sugar, saturated fat, sodium and potassium against their references,
                  water, protein at each meal and the three lowest vitamins or minerals with their share
                  of the daily amount. An empty meals list means nothing was
                  logged that day.""";
    }

    @Override
    public ToolResult invoke(CoachCallContext context, JsonNode args) {
        String requested = args.path("date").asText(null);
        if (requested == null || requested.isBlank()) {
            throw new IllegalArgumentException("readMealsOnDay needs a date as YYYY-MM-DD");
        }

        LocalDate date;
        try {
            date = LocalDate.parse(requested.strip());
        } catch (DateTimeParseException ex) {
            return ToolResult.of("date refused",
                    "That is not a date in the form YYYY-MM-DD. Ask again with a valid date, or say that you "
                            + "could not read the day.");
        }

        ZoneId zone = calendar.zone(context.userId());
        LocalDate today = calendar.today(zone);
        if (date.isAfter(today)) {
            return ToolResult.of("date refused",
                    "That day has not happened yet. Today is " + today + " in the user's timezone.");
        }
        if (date.isBefore(today.minusDays(MAX_DAYS_BACK))) {
            return ToolResult.of("date refused",
                    "Meals can only be read for the last " + MAX_DAYS_BACK + " days. Today is " + today
                            + " in the user's timezone.");
        }

        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Instant nextDayStart = date.plusDays(1).atStartOfDay(zone).toInstant();

        String mealsBody = client.meals(context.bearerToken(), dayStart, nextDayStart.minusNanos(1));
        if (mealsBody == null) {
            return unavailable();
        }
        try {
            JsonNode mealsJson = reader.readTree(mealsBody);
            if (!mealsJson.isArray()) {
                return unavailable();
            }
            List<JsonNode> meals = mealsOfTheDay(mealsJson, dayStart, nextDayStart);
            if (meals.isEmpty()) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("date", date.toString());
                empty.put("meals", List.of());
                empty.put("note", "Nothing was logged on that day.");
                return ToolResult.of("0 meals", write(empty));
            }

            String summaryBody = client.dailySummary(context.bearerToken(), date, zone.getId());
            if (summaryBody == null) {
                return unavailable();
            }
            JsonNode summary = reader.readTree(summaryBody);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("date", date.toString());
            result.put("totals", totals(summary));
            Map<String, Object> targets = targets(summary);
            if (!targets.isEmpty()) {
                result.put("targets", targets);
            }
            // The diary figures are an addition: when nutrition-service is older or the call fails
            // the line is simply absent and the result is what it was before.
            Map<String, Object> diary = DiaryFigures.day(summary, DiaryFigures.read(reader,
                    client.nutrientsSummary(context.bearerToken(), date, date, zone.getId())));
            if (!diary.isEmpty()) {
                result.put("diary", diary);
            }
            result.put("mealCount", meals.size());
            List<Map<String, Object>> listed = new ArrayList<>();
            for (JsonNode meal : meals.subList(0, Math.min(meals.size(), MAX_MEALS))) {
                listed.add(meal(meal, zone));
            }
            result.put("meals", listed);
            if (meals.size() > MAX_MEALS) {
                result.put("note", "Only the first " + MAX_MEALS + " meals of the day are listed.");
            }
            return ToolResult.of(meals.size() + " meals on " + date, write(result));
        } catch (JsonProcessingException ex) {
            return unavailable();
        }
    }

    private static ToolResult unavailable() {
        return ToolResult.of("meals unavailable",
                "The meals of that day could not be read. Answer without them, or say so.");
    }

    private String write(Map<String, Object> result) throws JsonProcessingException {
        return writer.writeValueAsString(result);
    }

    /** The meals inside the local day, earliest first, whatever order the service answered in. */
    private static List<JsonNode> mealsOfTheDay(JsonNode body, Instant dayStart, Instant nextDayStart) {
        List<JsonNode> meals = new ArrayList<>();
        for (JsonNode item : body) {
            Instant consumedAt = instantOf(item.path("consumedAt"));
            if (consumedAt != null && !consumedAt.isBefore(dayStart) && consumedAt.isBefore(nextDayStart)) {
                meals.add(item);
            }
        }
        meals.sort(Comparator.comparing(item -> instantOf(item.path("consumedAt"))));
        return meals;
    }

    private static Instant instantOf(JsonNode node) {
        if (!node.isTextual()) {
            return null;
        }
        try {
            return Instant.parse(node.asText());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static Map<String, Object> totals(JsonNode summary) {
        Map<String, Object> totals = new LinkedHashMap<>();
        put(totals, "calories", kcal(summary.path("totalCalories")));
        put(totals, "proteinGrams", grams(summary.path("totalProteinGrams")));
        put(totals, "carbohydrateGrams", grams(summary.path("totalCarbsGrams")));
        put(totals, "fatGrams", grams(summary.path("totalFatGrams")));
        return totals;
    }

    private static Map<String, Object> targets(JsonNode summary) {
        Map<String, Object> targets = new LinkedHashMap<>();
        put(targets, "calories", kcal(summary.path("targetCalories")));
        put(targets, "proteinGrams", grams(summary.path("targetProteinGrams")));
        put(targets, "carbohydrateGrams", grams(summary.path("targetCarbsGrams")));
        put(targets, "fatGrams", grams(summary.path("targetFatGrams")));
        return targets;
    }

    private static Map<String, Object> meal(JsonNode meal, ZoneId zone) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("mealType", meal.path("mealType").asText(""));
        entry.put("time", LOCAL_TIME.format(instantOf(meal.path("consumedAt")).atZone(zone)));
        put(entry, "calories", kcal(meal.path("totalCalories")));
        put(entry, "proteinGrams", grams(meal.path("totalProteinGrams")));
        List<String> foods = new ArrayList<>();
        for (JsonNode item : meal.path("items")) {
            String food = foodName(item.path("foodName").asText(""));
            if (!food.isEmpty() && foods.size() < MAX_FOODS_PER_MEAL) {
                foods.add(food);
            }
        }
        entry.put("foods", foods);
        return entry;
    }

    /** A user-typed name, trimmed and cut so it cannot crowd out the rest of the result. */
    static String foodName(String raw) {
        String name = raw.strip();
        if (name.length() <= MAX_FOOD_NAME_LENGTH) {
            return name;
        }
        int end = MAX_FOOD_NAME_LENGTH;
        if (Character.isHighSurrogate(name.charAt(end - 1))) {
            end--;
        }
        return name.substring(0, end).stripTrailing();
    }

    private static void put(Map<String, Object> into, String key, Object value) {
        if (value != null) {
            into.put(key, value);
        }
    }

    private static Long kcal(JsonNode node) {
        return node.isNumber() ? Math.round(node.asDouble()) : null;
    }

    /** One decimal, with a trailing zero dropped, as the computed facts write grams. */
    private static BigDecimal grams(JsonNode node) {
        if (!node.isNumber()) {
            return null;
        }
        BigDecimal rounded = node.decimalValue().setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
        return rounded.scale() < 0 ? rounded.setScale(0) : rounded;
    }
}
