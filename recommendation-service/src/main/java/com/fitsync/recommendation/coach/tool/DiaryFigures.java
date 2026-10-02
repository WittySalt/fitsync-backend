package com.fitsync.recommendation.coach.tool;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;

/**
 * The Nutrition Diary figures of a day or a week, shortened to what the coach needs to say.
 *
 * <p>Every field read here is optional. An older nutrition-service does not send them, and then the
 * result is empty and the tool answers exactly as it did before. Each figure is written as one short
 * phrase with the percentage already worked out, because the answer check accepts only numbers that
 * appear in a tool result: a share the model had to calculate itself would be rejected.
 */
final class DiaryFigures {

    /** How many of the lowest vitamins and minerals are named. */
    static final int LOWEST_COUNT = 3;

    private static final List<String> BALANCE_KEYS = List.of("fibre", "potassium", "sugar", "saturatedFat", "sodium");

    private DiaryFigures() {
    }

    /** The body as a tree, or null when there is no body or it is not JSON. */
    static JsonNode read(ObjectReader reader, String body) {
        if (body == null) {
            return null;
        }
        try {
            return reader.readTree(body);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    /**
     * The figures of one local day: the balance against the references, water, protein at each meal
     * and the lowest vitamins and minerals. Either argument may be null.
     */
    static Map<String, Object> day(JsonNode summary, JsonNode nutrients) {
        Map<String, Object> figures = new LinkedHashMap<>();
        if (summary != null) {
            balance(summary, figures);
            water(summary).ifPresent(water -> figures.put("water", water));
            proteinByMeal(summary.path("proteinByMeal")).ifPresent(text -> figures.put("proteinByMeal", text));
        }
        lowest(nutrients).ifPresent(lowest -> figures.put("lowestVitaminsAndMinerals", lowest));
        coverage(nutrients).ifPresent(note -> figures.put("vitaminDataNote", note));
        return figures;
    }

    /** The lowest three vitamins and minerals over a run of days, and any average above its upper limit. */
    static Map<String, Object> micronutrients(JsonNode nutrients) {
        Map<String, Object> figures = new LinkedHashMap<>();
        lowest(nutrients).ifPresent(lowest -> figures.put("lowest", lowest));
        List<String> above = aboveUpperLimit(nutrients);
        if (!above.isEmpty()) {
            figures.put("aboveUpperLimit", above);
        }
        coverage(nutrients).ifPresent(note -> figures.put("vitaminDataNote", note));
        return figures;
    }

    private static void balance(JsonNode summary, Map<String, Object> into) {
        JsonNode balance = summary.path("balance");
        if (!balance.isArray()) {
            return;
        }
        String sugarFromDrinks = number(summary.path("sugarFromDrinksGrams"));
        Map<String, String> byKey = new LinkedHashMap<>();
        for (JsonNode entry : balance) {
            String key = keyOf(entry.path("code").asText(""));
            String text = balanceLine(entry);
            if (key != null && text != null) {
                if ("sugar".equals(key) && sugarFromDrinks != null) {
                    text = text + ", " + sugarFromDrinks + " g of it from drinks";
                }
                byKey.put(key, text);
            }
        }
        for (String key : BALANCE_KEYS) {
            if (byKey.containsKey(key)) {
                into.put(key, byKey.get(key));
            }
        }
    }

    /** The codes are the contract's own, in capitals, so they are matched exactly. */
    private static String keyOf(String code) {
        return switch (code) {
            case "FIBER" -> "fibre";
            case "POTASSIUM" -> "potassium";
            case "SUGAR" -> "sugar";
            case "SAT_FAT" -> "saturatedFat";
            case "SODIUM" -> "sodium";
            default -> null;
        };
    }

    /** "22.4 g of 30 g (75%)" for a target, "62 g against a limit of 50 g (124%, over)" for a limit. */
    private static String balanceLine(JsonNode entry) {
        String amount = number(entry.path("amount"));
        String reference = number(entry.path("reference"));
        if (amount == null || reference == null) {
            return null;
        }
        String unit = unitSuffix(entry.path("unit"));
        String percent = percent(entry.path("share"));
        boolean limit = "LIMIT".equals(entry.path("kind").asText(""));
        StringBuilder line = new StringBuilder(amount).append(unit);
        line.append(limit ? " against a limit of " : " of ").append(reference).append(unit);
        if (percent != null) {
            line.append(" (").append(percent).append('%');
            if (limit && entry.path("over").asBoolean(false)) {
                line.append(", over");
            }
            line.append(')');
        }
        return line.toString();
    }

    private static Optional<String> water(JsonNode summary) {
        Long plain = millilitres(summary.path("waterMl"));
        Long drinks = millilitres(summary.path("waterFromDrinksMl"));
        if (plain == null && drinks == null) {
            return Optional.empty();
        }
        return Optional.of((plain == null ? 0 : plain) + " ml plain water and "
                + (drinks == null ? 0 : drinks) + " ml from drinks");
    }

    private static Optional<String> proteinByMeal(JsonNode protein) {
        JsonNode meals = protein.path("meals");
        if (!meals.isArray() || meals.isEmpty()) {
            return Optional.empty();
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode meal : meals) {
            String grams = number(meal.path("grams"));
            String type = meal.path("mealType").asText("");
            if (grams != null && !type.isBlank()) {
                parts.add(type.toLowerCase(Locale.ROOT) + " " + grams + " g");
            }
        }
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        String text = String.join(", ", parts);
        String low = number(protein.path("lowGrams"));
        String high = number(protein.path("highGrams"));
        if (low != null && high != null) {
            text = text + "; a meal is well served by " + low + " to " + high + " g";
        }
        return Optional.of(text);
    }

    /** The lowest vitamins and minerals by share of their daily amount, lowest first. */
    private static Optional<List<String>> lowest(JsonNode nutrients) {
        if (nutrients == null || !nutrients.path("nutrients").isArray() || noDaysLogged(nutrients)) {
            return Optional.empty();
        }
        List<JsonNode> candidates = new ArrayList<>();
        for (JsonNode nutrient : nutrients.path("nutrients")) {
            if (isVitaminOrMineral(nutrient) && nutrient.path("share").isNumber()
                    && nutrient.path("reference").isNumber() && nutrient.path("reference").asDouble() > 0) {
                candidates.add(nutrient);
            }
        }
        candidates.sort(Comparator.comparingDouble(node -> node.path("share").asDouble()));
        List<String> lines = new ArrayList<>();
        for (JsonNode nutrient : candidates.subList(0, Math.min(LOWEST_COUNT, candidates.size()))) {
            String amount = number(nutrient.path("averageAmount"));
            lines.add(name(nutrient) + " " + percent(nutrient.path("share")) + "% ("
                    + (amount == null ? "0" : amount) + " of " + number(nutrient.path("reference"))
                    + unitSuffix(nutrient.path("unit")) + ")");
        }
        return lines.isEmpty() ? Optional.empty() : Optional.of(lines);
    }

    private static List<String> aboveUpperLimit(JsonNode nutrients) {
        List<String> lines = new ArrayList<>();
        if (nutrients == null || !nutrients.path("nutrients").isArray()) {
            return lines;
        }
        for (JsonNode nutrient : nutrients.path("nutrients")) {
            String average = number(nutrient.path("averageAmount"));
            String upper = number(nutrient.path("upperLimit"));
            if (nutrient.path("aboveUpperLimit").asBoolean(false) && average != null && upper != null) {
                String unit = unitSuffix(nutrient.path("unit"));
                lines.add(name(nutrient) + " " + average + unit + " on average, above its upper limit of "
                        + upper + unit);
            }
        }
        return lines;
    }

    private static Optional<String> coverage(JsonNode nutrients) {
        if (nutrients == null || noDaysLogged(nutrients)) {
            return Optional.empty();
        }
        JsonNode coverage = nutrients.path("coverage");
        if (coverage.path("itemsLogged").isInt() && coverage.path("itemsWithoutNutrients").isInt()
                && coverage.path("itemsWithoutNutrients").asInt() > 0) {
            return Optional.of(coverage.path("itemsWithoutNutrients").asInt() + " of "
                    + coverage.path("itemsLogged").asInt() + " logged foods have no vitamin or mineral data");
        }
        return Optional.empty();
    }

    private static boolean noDaysLogged(JsonNode nutrients) {
        return nutrients.path("daysLogged").isInt() && nutrients.path("daysLogged").asInt() == 0;
    }

    private static boolean isVitaminOrMineral(JsonNode nutrient) {
        String kind = nutrient.path("kind").asText("");
        return "VITAMIN".equals(kind) || "MINERAL".equals(kind);
    }

    private static String name(JsonNode nutrient) {
        return ReadMealsOnDayTool.foodName(nutrient.path("displayName").asText(nutrient.path("code").asText("")));
    }

    private static String unitSuffix(JsonNode unit) {
        return unit.isTextual() && !unit.asText().isBlank() ? " " + unit.asText().strip() : "";
    }

    private static Long millilitres(JsonNode node) {
        return node.isNumber() ? Long.valueOf(Math.round(node.asDouble())) : null;
    }

    static String percent(JsonNode share) {
        return share.isNumber() ? Long.toString(Math.round(share.asDouble() * 100)) : null;
    }

    /** One decimal with a trailing zero dropped, as the other tools write grams, or null when absent. */
    static String number(JsonNode node) {
        if (!node.isNumber()) {
            return null;
        }
        return node.decimalValue().setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
