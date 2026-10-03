package com.fitsync.recommendation.coach.tool;

import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.MAX_ITEMS;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.absolute;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.line;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.number;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.sign;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.summaryLines;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.withPrevious;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.words;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.client.AnalyticsProgressClient;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import org.springframework.stereotype.Component;

/**
 * The Food page for a period, read from analytics-service: calories and protein a day on logged days
 * against the period before, the energy balance, the macros against their targets, the plate by
 * share of calories, the meals and the habits.
 */
@Component
public class ReadFoodProgressTool extends ProgressReadTool {

    private final AnalyticsProgressClient client;

    public ReadFoodProgressTool(AnalyticsProgressClient client, ObjectMapper objectMapper, UserCalendar calendar) {
        super(objectMapper, calendar);
        this.client = client;
    }

    @Override
    public String name() {
        return "readFoodProgress";
    }

    @Override
    public String promptSignature() {
        return """
                readFoodProgress(from?, to?)
                  from, to: dates like 2026-09-01, at most 370 days apart; default the last 28 days
                  returns the Food page for that period: calories and protein a day on logged days against
                  the period before, energy balance, macros against targets, plate by share of calories,
                  meals, habits""";
    }

    @Override
    protected String subject() {
        return "Food progress";
    }

    @Override
    protected String fetch(CoachCallContext context, JsonNode args, ProgressToolSupport.Period period) {
        return client.foodProgress(context.bearerToken(), context.timezone(), period.from(), period.to());
    }

    @Override
    protected String render(JsonNode body, ProgressToolSupport.Period period) {
        StringBuilder out = new StringBuilder();
        appendHero(out, body.path("hero"));
        ProgressToolSupport.appendHighlights(out, body.path("highlights"));
        appendMacros(out, body.path("macros"));
        appendPlate(out, body.path("plate"));
        appendMeals(out, body.path("meals").path("share"));
        appendHabits(out, body.path("habits"));
        if (out.length() == 0) {
            return "";
        }
        return "Food from " + period.describe() + ". Figures a day count only the days with food logged.\n" + out;
    }

    private static void appendHero(StringBuilder out, JsonNode hero) {
        String eaten = withPrevious(hero.path("eatenPerDay"), "kcal");
        if (eaten != null) {
            line(out, "Calories eaten a day: " + eaten + ".");
        }
        String protein = withPrevious(hero.path("proteinPerDay"), "g");
        if (protein != null) {
            line(out, "Protein a day: " + protein + ".");
        }
        JsonNode balance = hero.path("balancePerDay");
        String balanceValue = absolute(balance.path("value"));
        if (balanceValue != null) {
            int direction = sign(balance.path("value"));
            String days = number(balance.path("daysLogged"));
            line(out, "Energy balance a day (eaten minus used): "
                    + (direction == 0 ? "even, eaten matched used"
                    : balanceValue + " kcal " + (direction < 0 ? "under" : "over") + " what was used")
                    + (days == null ? "" : ", over " + days + " logged days") + ".");
        }
        JsonNode logged = hero.path("daysLogged");
        String loggedNow = number(logged.path("value"));
        if (loggedNow != null) {
            String days = number(logged.path("days"));
            String previous = number(logged.path("previous"));
            line(out, "Days with food logged: " + loggedNow + (days == null ? "" : " of " + days)
                    + (previous == null ? "" : ", and " + previous + " in the period before") + ".");
        }
    }

    private static void appendMacros(StringBuilder out, JsonNode macros) {
        appendMacro(out, macros.path("protein"), "Protein");
        appendMacro(out, macros.path("carbs"), "Carbs");
        appendMacro(out, macros.path("fat"), "Fat");
        List<String> summary = summaryLines(macros.path("summary"));
        if (!summary.isEmpty()) {
            line(out, "Macro summary: " + String.join("; ", summary) + ".");
        }
    }

    private static void appendMacro(StringBuilder out, JsonNode macro, String label) {
        String first = number(macro.path("firstWeek"));
        String latest = number(macro.path("thisWeek"));
        String target = number(macro.path("target"));
        if (first == null && latest == null) {
            return;
        }
        List<String> parts = new ArrayList<>();
        if (first != null) {
            parts.add(first + " g a day in the first week");
        }
        if (latest != null) {
            parts.add(latest + " g a day in the latest week");
        }
        if (target != null) {
            parts.add("target " + target + " g");
        }
        line(out, label + ": " + String.join(", ", parts) + ".");
    }

    private static void appendPlate(StringBuilder out, JsonNode plate) {
        List<String> lines = new ArrayList<>();
        for (JsonNode group : plate.path("current")) {
            String name = words(group.path("group").asText(""));
            String share = percent(group.path("share"));
            if (name.isEmpty() || share == null) {
                continue;
            }
            String delta = deltaFor(plate.path("changes"), group.path("group").asText(""));
            lines.add("- " + name + ": " + share + "%" + (delta == null ? "" : ", " + delta));
            if (lines.size() >= MAX_ITEMS) {
                break;
            }
        }
        if (!lines.isEmpty()) {
            line(out, "Plate by share of calories:");
            lines.forEach(group -> line(out, group));
        }
    }

    /** A share given as 0 to 1 is written as a percentage; one already over 1 is taken as one. */
    private static String percent(JsonNode share) {
        if (!share.isNumber()) {
            return null;
        }
        java.math.BigDecimal value = share.decimalValue();
        if (value.compareTo(java.math.BigDecimal.ONE) <= 0) {
            value = value.multiply(java.math.BigDecimal.valueOf(100));
        }
        return ProgressToolSupport.format(value.setScale(0, java.math.RoundingMode.HALF_UP));
    }

    private static String deltaFor(JsonNode changes, String group) {
        for (JsonNode change : changes) {
            if (group.equals(change.path("group").asText())) {
                String points = absolute(change.path("deltaPoints"));
                int direction = sign(change.path("deltaPoints"));
                if (points == null || direction == 0) {
                    return null;
                }
                return points + " points " + (direction < 0 ? "lower" : "higher") + " than the period before";
            }
        }
        return null;
    }

    private static void appendMeals(StringBuilder out, JsonNode shares) {
        List<String> parts = new ArrayList<>();
        for (JsonNode meal : shares) {
            String name = words(meal.path("mealType").asText(""));
            String share = number(meal.path("sharePct"));
            if (!name.isEmpty() && share != null) {
                parts.add(name + " " + share + "%");
            }
        }
        if (!parts.isEmpty()) {
            line(out, "Share of calories by meal: " + String.join(", ", parts) + ".");
        }
    }

    private static void appendHabits(StringBuilder out, JsonNode habits) {
        String water = number(habits.path("waterMetDays"));
        String fibre = number(habits.path("fibreMetDays"));
        String logged = number(habits.path("loggedDays"));
        if (water == null && fibre == null) {
            return;
        }
        List<String> parts = new ArrayList<>();
        if (water != null) {
            parts.add("water target met on " + water + " days");
        }
        if (fibre != null) {
            parts.add("fibre target met on " + fibre + " days");
        }
        line(out, "Habits: " + String.join(", ", parts) + (logged == null ? "" : ", of " + logged + " logged days") + ".");
    }
}
