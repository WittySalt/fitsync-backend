package com.fitsync.recommendation.coach.tool;

import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.MAX_ITEMS;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.absolute;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.line;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.number;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.sign;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.text;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.words;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.client.AnalyticsProgressClient;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import org.springframework.stereotype.Component;

/**
 * The Goal page for a period, read from analytics-service: the weight goal, the weight now and its
 * change, the weekly rate and projected date against the period before, body measures and the other
 * goals with the days they were met.
 */
@Component
public class ReadGoalProgressTool extends ProgressReadTool {

    /** The road's state codes as the coach says them; a code missing here is left out of the prompt. */
    private static final Map<String, String> STATE_PHRASES = Map.of(
            "AHEAD", "ahead",
            "ON_COURSE", "on course",
            "BEHIND", "behind",
            "REACHED", "reached",
            "DATE_PASSED", "past the date",
            "STALLED", "holding steady");

    private final AnalyticsProgressClient client;

    public ReadGoalProgressTool(AnalyticsProgressClient client, ObjectMapper objectMapper, UserCalendar calendar) {
        super(objectMapper, calendar);
        this.client = client;
    }

    @Override
    public String name() {
        return "readGoalProgress";
    }

    @Override
    public String promptSignature() {
        return """
                readGoalProgress(from?, to?)
                  from, to: dates like 2026-09-01, at most 370 days apart; default the last 28 days
                  returns the Goal page for that period: weight goal, weight now and its change, weekly rate
                  and projected date against the period before, how far along the road to the goal the weight is
                  (ahead, on course, behind, reached), body measures, other goals and days met""";
    }

    @Override
    protected String subject() {
        return "Goal progress";
    }

    @Override
    protected String fetch(CoachCallContext context, JsonNode args, ProgressToolSupport.Period period) {
        return client.goalProgress(context.bearerToken(), context.timezone(), period.from(), period.to());
    }

    @Override
    protected String render(JsonNode body, ProgressToolSupport.Period period) {
        StringBuilder out = new StringBuilder();
        appendGoal(out, body.path("goal"));
        appendHero(out, body.path("hero"));
        appendRoad(out, body.path("road"), body.path("leadType"));
        ProgressToolSupport.appendHighlights(out, body.path("highlights"));
        appendWeeklyWeight(out, body.path("weight"));
        appendBody(out, body.path("body"));
        appendOtherGoals(out, body.path("otherGoals"));
        if (out.length() == 0) {
            return "";
        }
        return "Goal from " + period.describe() + ".\n" + out;
    }

    private static void appendGoal(StringBuilder out, JsonNode goal) {
        if (goal.isMissingNode()) {
            return;
        }
        if (goal.isNull()) {
            line(out, "There is no weight goal set.");
            return;
        }
        String target = number(goal.path("targetKg"));
        if (target == null) {
            return;
        }
        String by = goal.path("byDate").asText("");
        line(out, "Weight goal: " + target + " kg" + (by.isBlank() ? "" : " by " + ProgressToolSupport.clean(by)) + ".");
    }

    private static void appendHero(StringBuilder out, JsonNode hero) {
        String now = number(hero.path("nowKg"));
        if (now != null) {
            line(out, "Weight now: " + now + " kg.");
        }
        String change = absolute(hero.path("changeKg"));
        if (change != null) {
            int direction = sign(hero.path("changeKg"));
            line(out, direction == 0
                    ? "Change over the period: the weight stayed the same."
                    : "Change over the period: " + change + " kg " + (direction < 0 ? "down" : "up") + ".");
        }
        JsonNode rate = hero.path("weeklyRate");
        String rateNow = absolute(rate.path("value"));
        if (rateNow != null) {
            String previous = absolute(rate.path("previous"));
            line(out, "Weekly rate: " + rateNow + " kg a week " + direction(rate.path("value"))
                    + (previous == null ? "" : ", and " + previous + " kg a week "
                    + direction(rate.path("previous")) + " in the period before") + ".");
        }
        JsonNode projected = hero.path("projectedDate");
        String projectedNow = projected.path("value").asText("");
        if (!projectedNow.isBlank()) {
            String previous = projected.path("previous").asText("");
            line(out, "Date the goal is reached at this rate: " + ProgressToolSupport.clean(projectedNow)
                    + (previous.isBlank() ? "" : ", and " + ProgressToolSupport.clean(previous)
                    + " at the rate of the period before") + ".");
        }
        String daysLeft = number(hero.path("daysLeft"));
        if (daysLeft != null) {
            line(out, "Days left to the goal date: " + daysLeft + ".");
        }
    }

    /**
     * The road to the weight goal. The state and direction codes are written as plain phrases, because an
     * answer that copies {@code AHEAD} or {@code DATE_PASSED} is rejected.
     */
    private static void appendRoad(StringBuilder out, JsonNode road, JsonNode leadType) {
        String lead = words(leadType.asText(""));
        if (!lead.isEmpty()) {
            line(out, "The goal the page leads with: " + lead + ".");
        }
        if (!road.isObject()) {
            return;
        }
        String direction = switch (road.path("direction").asText("")) {
            case "LOSE" -> "losing weight";
            case "GAIN" -> "gaining weight";
            case "KEEP" -> "keeping the weight steady";
            default -> "";
        };
        String state = STATE_PHRASES.get(road.path("state").asText(""));
        if (state == null) {
            line(out, "The road to the goal has no pace yet, because there are too few weigh-ins.");
        } else {
            line(out, "Road to the goal: " + state + (direction.isEmpty() ? "" : ", " + direction) + ".");
        }
        String distance = number(road.path("distanceKg"));
        if (distance != null) {
            line(out, "Distance to the goal weight: " + distance + " kg.");
        }
        String projected = road.path("projectedDate").asText("");
        if (!projected.isBlank()) {
            line(out, "Date the road reaches the goal weight: " + ProgressToolSupport.clean(projected) + ".");
        }
        String weight = percent(road.path("fractionWeight"));
        String time = percent(road.path("fractionTime"));
        if (weight != null) {
            line(out, "Share of the way to the goal weight: " + weight + "%"
                    + (time == null ? "" : ", with " + time + "% of the time to the goal date gone") + ".");
        }
        JsonNode weeks = road.path("keep").path("weeks");
        if (weeks.isArray() && !weeks.isEmpty()) {
            int inside = 0;
            for (JsonNode week : weeks) {
                inside += week.path("inside").asBoolean(false) ? 1 : 0;
            }
            String band = number(road.path("keep").path("bandKg"));
            line(out, "Weeks with the average weight within " + (band == null ? "1" : band)
                    + " kg of the goal weight: " + inside + " of " + weeks.size() + ".");
        }
    }

    private static String percent(JsonNode fraction) {
        if (!fraction.isNumber()) {
            return null;
        }
        return ProgressToolSupport.format(fraction.decimalValue().movePointRight(2));
    }

    private static String direction(JsonNode value) {
        int sign = sign(value);
        return sign < 0 ? "down" : sign > 0 ? "up" : "with no change";
    }

    private static void appendWeeklyWeight(StringBuilder out, JsonNode weight) {
        List<String> lines = new ArrayList<>();
        JsonNode weekly = weight.path("weekly");
        int start = Math.max(0, weekly.size() - MAX_ITEMS);
        for (int i = start; i < weekly.size(); i++) {
            JsonNode week = weekly.get(i);
            String average = number(week.path("avgKg"));
            String weekStart = week.path("weekStart").asText("");
            if (average == null || weekStart.isBlank()) {
                continue;
            }
            String weighIns = number(week.path("weighIns"));
            lines.add("- week of " + ProgressToolSupport.clean(weekStart) + ": " + average + " kg"
                    + (weighIns == null ? "" : " from " + weighIns + " weigh-ins"));
        }
        if (!lines.isEmpty()) {
            line(out, "Average weight a week:");
            lines.forEach(week -> line(out, week));
        }
        String method = text(weight, "method");
        if (!method.isEmpty()) {
            line(out, "The projection uses the " + method + ".");
        }
    }

    private static void appendBody(StringBuilder out, JsonNode body) {
        appendMeasure(out, body.path("bodyFat"), "Body fat", "%");
        appendMeasure(out, body.path("waist"), "Waist", "cm");
        appendMeasure(out, body.path("bmi"), "BMI", "");
    }

    private static void appendMeasure(StringBuilder out, JsonNode measure, String label, String unit) {
        String start = number(measure.path("start"));
        String end = number(measure.path("end"));
        if (start == null && end == null) {
            return;
        }
        String suffix = unit.isEmpty() ? "" : ("%".equals(unit) ? "%" : " " + unit);
        StringBuilder item = new StringBuilder(label).append(": ");
        if (start != null && end != null) {
            item.append(start).append(suffix).append(" at the start and ").append(end).append(suffix).append(" at the end");
        } else {
            item.append(start != null ? start : end).append(suffix);
        }
        String context = text(measure, "context");
        if (!context.isEmpty()) {
            item.append(". ").append(context);
        }
        line(out, item.append('.').toString());
    }

    private static void appendOtherGoals(StringBuilder out, JsonNode goals) {
        List<String> lines = new ArrayList<>();
        for (JsonNode goal : goals) {
            String type = words(goal.path("type").asText(""));
            String target = number(goal.path("target"));
            if (type.isEmpty() || target == null) {
                continue;
            }
            String unit = text(goal, "unit");
            StringBuilder item = new StringBuilder("- ").append(type).append(", target ").append(target)
                    .append(unit.isEmpty() ? "" : " " + words(unit));
            String met = number(goal.path("metDays"));
            String logged = number(goal.path("loggedDays"));
            if (met != null && logged != null) {
                item.append(": met on ").append(met).append(" of ").append(logged).append(" logged days");
            }
            List<String> weeks = new ArrayList<>();
            for (JsonNode week : goal.path("weeks")) {
                String value = number(week.path("value"));
                String weekStart = week.path("weekStart").asText("");
                if (value != null && !weekStart.isBlank()) {
                    weeks.add("week of " + ProgressToolSupport.clean(weekStart) + " " + value);
                }
            }
            if (!weeks.isEmpty()) {
                int from = Math.max(0, weeks.size() - MAX_ITEMS);
                item.append("; ").append(String.join(", ", weeks.subList(from, weeks.size())));
            }
            lines.add(item.append('.').toString());
        }
        if (!lines.isEmpty()) {
            line(out, "Other goals:");
            lines.forEach(goal -> line(out, goal));
        }
    }
}
