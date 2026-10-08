package com.fitsync.recommendation.coach.tool;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.client.WorkoutCatalogueClient;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import org.springframework.stereotype.Component;

/**
 * What the user actually trained lately, read from workout-service.
 *
 * <p>Distinct from {@code readFacts}, which carries this week's counts from the projection. This
 * returns the sessions themselves, because a question like "what should I train today" turns on
 * which days were used and what was done on them, not on a weekly total.
 *
 * <p>Sessions are dated in the user's own zone, with the weekday written out. workout-service sends
 * instants in UTC, and a morning session in Singapore falls on the UTC day before; cutting the date
 * off the instant told the model "Wednesday" for a session the user did on Thursday morning.
 */
@Component
public class ReadRecentTrainingTool implements CoachTool {

    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 90;
    private static final int PAGE_SIZE = 20;

    private final WorkoutCatalogueClient client;
    private final ObjectMapper objectMapper;
    private final UserCalendar calendar;

    public ReadRecentTrainingTool(WorkoutCatalogueClient client, ObjectMapper objectMapper, UserCalendar calendar) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.calendar = calendar;
    }

    @Override
    public String name() {
        return "readRecentTraining";
    }

    @Override
    public ToolClass toolClass() {
        return ToolClass.READ;
    }

    @Override
    public String promptSignature() {
        return """
                readRecentTraining(days?)
                  days: 1 to 90, default 7
                  returns one line per completed session: "weekday date | name | type | minutes", dated
                  in the user's timezone, and for a session with time segments how long each ran
                  against its plan""";
    }

    @Override
    public ToolResult invoke(CoachCallContext context, JsonNode args) {
        int days = args.path("days").asInt(DEFAULT_DAYS);
        days = Math.max(1, Math.min(days, MAX_DAYS));
        ZoneId zone = calendar.zone(context);
        LocalDate from = calendar.today(zone).minusDays(days - 1L);

        String body = client.completedSessions(context.bearerToken(), from.atStartOfDay(zone).toInstant(), PAGE_SIZE);
        if (body == null) {
            return ToolResult.of("history unavailable",
                    "Recent training history could not be read. Answer without it, or say so.");
        }

        List<String> rows = project(body, zone);
        if (rows.isEmpty()) {
            return ToolResult.of("0 sessions",
                    "No completed sessions in the last " + days + " days.");
        }
        return ToolResult.of(rows.size() + " sessions in " + days + "d", String.join("\n", rows));
    }

    private List<String> project(String body, ZoneId zone) {
        List<String> rows = new ArrayList<>();
        try {
            JsonNode content = objectMapper.readTree(body).path("content");
            for (JsonNode item : content) {
                String row = "%s | %s | %s | %s min".formatted(
                        localDay(item, zone),
                        item.path("name").asText(""),
                        item.path("workoutType").asText(""),
                        item.path("durationMinutes").asText("?"));
                String segments = segmentWords(item.path("entries"));
                rows.add(segments.isEmpty() ? row : row + " | " + segments);
            }
        } catch (JsonProcessingException ex) {
            // A catalogue that answered with something unparseable is a missing result, not a
            // failed turn: the model is told there were no candidates and works around it.
            return List.of();
        }
        return rows;
    }

    /**
     * The day a session was done in the user's zone, as "Thursday 2026-10-08": the completion time,
     * else the start time. Empty when neither is a readable instant.
     */
    static String localDay(JsonNode session, ZoneId zone) {
        return UserCalendar.localDate(session.path("completedAt").asText(null), zone)
                .or(() -> UserCalendar.localDate(session.path("startedAt").asText(null), zone))
                .map(day -> day.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + day)
                .orElse("");
    }

    /**
     * The time segments of a session in words: "steady ran 26 minutes against 20 planned" for each
     * one, the intervals counted together ("work intervals: 7 of 8 done"), "skipped" or "not run" for
     * the others. Empty for a session without segments.
     */
    static String segmentWords(JsonNode entries) {
        List<String> parts = new ArrayList<>();
        int workDone = 0;
        int work = 0;
        for (JsonNode entry : entries) {
            if (!"SEGMENT".equals(entry.path("entryType").asText())) {
                continue;
            }
            String kind = entry.path("kind").asText("");
            String status = entry.path("status").asText("");
            if ("WORK".equals(kind)) {
                work++;
                if ("DONE".equals(status)) {
                    workDone++;
                }
                continue;
            }
            if ("EASY".equals(kind)) {
                continue;
            }
            String label = ReadPlanTool.WORDS.getOrDefault(kind, entry.path("label").asText(kind).strip());
            int planned = minutes(entry.path("plannedSeconds"));
            if ("SKIPPED".equals(status)) {
                parts.add(label + " skipped");
            } else if (entry.path("actualSeconds").isNumber()) {
                parts.add(label + " ran " + minutes(entry.path("actualSeconds")) + " minutes against "
                        + planned + " planned");
            } else {
                parts.add(label + " not run, " + planned + " minutes planned");
            }
        }
        if (work > 0) {
            parts.add("work intervals: " + workDone + " of " + work + " done");
        }
        return String.join("; ", parts);
    }

    private static int minutes(JsonNode seconds) {
        return Math.round(seconds.asInt(0) / 60f);
    }
}
