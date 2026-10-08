package com.fitsync.recommendation.coach.tool;

import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.MAX_ITEMS;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.activityName;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.line;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.number;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.summaryLines;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.text;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.words;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.client.WorkoutCatalogueClient;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import org.springframework.stereotype.Component;

/**
 * One activity's page for a period, read from workout-service: minutes, sessions, weeks in a row,
 * the longest session, the usual time, effort and the weekly minutes.
 */
@Component
public class ReadActivityProgressTool extends ProgressReadTool {

    /** The same shape the message context accepts for an activity id, so nothing else reaches the path. */
    private static final Pattern KEY = Pattern.compile("^[A-Z_:]{1,40}$");

    private static final String[] WEEKDAYS = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

    private final WorkoutCatalogueClient client;

    public ReadActivityProgressTool(WorkoutCatalogueClient client, ObjectMapper objectMapper, UserCalendar calendar) {
        super(objectMapper, calendar);
        this.client = client;
    }

    @Override
    public String name() {
        return "readActivityProgress";
    }

    @Override
    public String promptSignature() {
        return """
                readActivityProgress(key, from?, to?)
                  key: STRENGTH, CARDIO, MOBILITY or SPORT:<activity>, for example SPORT:BADMINTON; from, to:
                  dates, at most 370 days apart, default the last 28 days
                  returns that activity's page: minutes, sessions, weeks in a row, longest session, usual
                  time, effort, minutes a week""";
    }

    @Override
    protected String subject() {
        return "Activity progress";
    }

    @Override
    protected String fetch(CoachCallContext context, JsonNode args, ProgressToolSupport.Period period) {
        String key = args.path("key").asText("").strip().toUpperCase(Locale.ROOT);
        if (!KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("key must be STRENGTH, CARDIO, MOBILITY or SPORT:<activity>");
        }
        return client.activityProgress(context.bearerToken(), context.timezone(), key, period.from(), period.to());
    }

    @Override
    protected String render(JsonNode body, ProgressToolSupport.Period period) {
        StringBuilder out = new StringBuilder();
        String name = text(body, "name");
        if (name.isEmpty()) {
            name = activityName(body.path("key").asText(""));
        }
        JsonNode tiles = body.path("tiles");
        String minutes = number(tiles.path("minutes"));
        String sessions = number(tiles.path("sessions"));
        if (minutes != null || sessions != null) {
            line(out, "Sessions: " + (sessions == null ? "unknown" : sessions)
                    + (minutes == null ? "" : ", " + minutes + " minutes in all") + ".");
        }
        String weeksInARow = number(tiles.path("weeksInARow"));
        if (weeksInARow != null) {
            line(out, "Weeks in a row with this activity: " + weeksInARow + ".");
        }
        JsonNode longest = tiles.path("longestSession");
        String longestMinutes = number(longest.path("minutes"));
        if (longestMinutes != null) {
            String date = longest.path("date").asText("");
            line(out, "Longest session: " + longestMinutes + " minutes"
                    + (date.isBlank() ? "" : " on " + ProgressToolSupport.clean(date)) + ".");
        }
        String usual = usualTime(tiles.path("usualTime"));
        if (usual != null) {
            line(out, "Usual time: " + usual + ".");
        }
        JsonNode effort = tiles.path("effort");
        String average = number(effort.path("average"));
        if (average != null) {
            String first = number(effort.path("first"));
            String last = number(effort.path("last"));
            line(out, "Effort out of 10: " + average + " on average"
                    + (first == null || last == null ? "" : ", " + first + " in the first session and " + last
                    + " in the latest") + ".");
        }
        appendWeekly(out, body.path("weekly"));
        String context = text(body, "context");
        if (!context.isEmpty()) {
            line(out, context + ".");
        }
        List<String> summary = summaryLines(body.path("summary"));
        if (!summary.isEmpty()) {
            line(out, "Summary: " + String.join("; ", summary) + ".");
        }
        if (out.length() == 0) {
            return "";
        }
        String kind = words(body.path("kind").asText(""));
        boolean kindIsName = name.equals(activityName(body.path("kind").asText("")));
        return name + (kind.isEmpty() || kindIsName ? "" : " (" + kind + ")")
                + " from " + period.describe() + ".\n" + out;
    }

    /** {@code {weekday: 2, hour: 18}} as "Tuesday around 18:00"; weekday 1 is Monday, as in java.time. */
    private static String usualTime(JsonNode usual) {
        JsonNode weekday = usual.path("weekday");
        JsonNode hour = usual.path("hour");
        if (!hour.isInt()) {
            return null;
        }
        String day = "";
        if (weekday.isInt() && weekday.asInt() >= 1 && weekday.asInt() <= 7) {
            day = WEEKDAYS[weekday.asInt() - 1] + " ";
        } else if (weekday.isTextual()) {
            day = ProgressToolSupport.activityName(weekday.asText()) + " ";
        }
        int clampedHour = Math.max(0, Math.min(23, hour.asInt()));
        return day + "around " + String.format(Locale.ROOT, "%02d:00", clampedHour);
    }

    private static void appendWeekly(StringBuilder out, JsonNode weekly) {
        List<String> parts = new ArrayList<>();
        int start = Math.max(0, weekly.size() - MAX_ITEMS);
        for (int i = start; i < weekly.size(); i++) {
            JsonNode week = weekly.get(i);
            String weekStart = week.path("weekStart").asText("");
            String minutes = number(week.path("minutes"));
            if (weekStart.isBlank() || minutes == null) {
                continue;
            }
            parts.add("week of " + ProgressToolSupport.clean(weekStart) + " " + minutes + " minutes"
                    + (week.path("current").asBoolean(false) ? " so far" : ""));
        }
        if (!parts.isEmpty()) {
            line(out, "Minutes a week: " + String.join(", ", parts) + ".");
        }
    }
}
