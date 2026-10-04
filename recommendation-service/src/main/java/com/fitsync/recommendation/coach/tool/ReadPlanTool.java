package com.fitsync.recommendation.coach.tool;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.client.WorkoutCatalogueClient;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import org.springframework.stereotype.Component;

/**
 * The person's current plan, read from workout-service and written in words: each training day with
 * its kind, length and what it holds (moves, or time segments for a cardio or sport day), the rest
 * days, the planned hard sets a week by muscle group and the plan's coverage notes. Codes never reach
 * the model; a day kind is "strength", a note is its sentence.
 */
@Component
public class ReadPlanTool implements CoachTool {

    static final int MAX_MOVES_NAMED = 4;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int EASY_EFFORT = 3;
    private static final int MODERATE_EFFORT = 6;
    private static final int HARD_EFFORT = 8;

    /** Codes in the words the coach uses, so no code and no case mapping reaches the answer. */
    static final Map<String, String> WORDS = Map.ofEntries(
            Map.entry("STRENGTH", "strength"), Map.entry("CARDIO", "cardio"), Map.entry("MOBILITY", "mobility"),
            Map.entry("SPORT", "sport"), Map.entry("WARMUP", "warm-up"), Map.entry("STEADY", "steady"),
            Map.entry("WORK", "work"), Map.entry("EASY", "easy"), Map.entry("COOLDOWN", "cool-down"),
            Map.entry("CHEST", "chest"), Map.entry("BACK", "back"), Map.entry("SHOULDERS", "shoulders"),
            Map.entry("ARMS", "arms"), Map.entry("CORE", "core"), Map.entry("GLUTES", "glutes"),
            Map.entry("LEGS", "legs"));

    private final WorkoutCatalogueClient client;
    private final ObjectMapper objectMapper;

    public ReadPlanTool(WorkoutCatalogueClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "readPlan";
    }

    @Override
    public ToolClass toolClass() {
        return ToolClass.READ;
    }

    @Override
    public String promptSignature() {
        return """
                readPlan()
                  returns the current plan: each training day with its kind, minutes and moves or time
                  segments, the rest days, planned hard sets a week by muscle group, and what the plan
                  leaves out""";
    }

    @Override
    public ToolResult invoke(CoachCallContext context, JsonNode args) {
        String body = client.activePlan(context.bearerToken());
        if (body == null) {
            return ToolResult.of("no plan", "No current plan could be read. Answer without it, or say so.");
        }
        try {
            JsonNode plan = objectMapper.readTree(body);
            return ToolResult.of("plan read", render(plan));
        } catch (JsonProcessingException ex) {
            return ToolResult.of("no plan", "No current plan could be read. Answer without it, or say so.");
        }
    }

    static String render(JsonNode plan) {
        List<String> lines = new ArrayList<>();
        JsonNode days = plan.path("days");
        lines.add("Plan: " + text(plan.path("name")) + ", " + days.size() + (days.size() == 1 ? " day" : " days")
                + " a week.");
        Set<Integer> trainingDays = new TreeSet<>();
        for (JsonNode day : days) {
            int dayOfWeek = day.path("dayOfWeek").asInt(0);
            if (dayOfWeek >= 1 && dayOfWeek <= 7) {
                trainingDays.add(dayOfWeek);
            }
            lines.add(dayLine(day));
        }
        if (!days.isEmpty()) {
            List<String> rest = new ArrayList<>();
            for (int dayOfWeek = 1; dayOfWeek <= 7; dayOfWeek++) {
                if (!trainingDays.contains(dayOfWeek)) {
                    rest.add(weekday(dayOfWeek));
                }
            }
            lines.add(rest.isEmpty() ? "No rest day." : "Rest days: " + String.join(", ", rest) + ".");
        }
        JsonNode coverage = plan.path("coverage");
        List<String> groups = new ArrayList<>();
        for (JsonNode group : coverage.path("groups")) {
            String code = text(group.path("group"));
            groups.add(WORDS.getOrDefault(code, code) + " " + number(group.path("setsPerWeek")));
        }
        if (!groups.isEmpty()) {
            lines.add("Planned hard sets a week by muscle group: " + String.join(", ", groups) + ".");
        }
        List<String> notes = new ArrayList<>();
        for (JsonNode note : coverage.path("notes")) {
            String sentence = text(note.path("text"));
            if (!sentence.isEmpty()) {
                notes.add(sentence);
            }
        }
        if (!notes.isEmpty()) {
            lines.add("What the plan leaves out: " + String.join(" ", notes));
        }
        return String.join("\n", lines);
    }

    private static String dayLine(JsonNode day) {
        String code = text(day.path("kind"));
        String kind = WORDS.getOrDefault(code, code);
        StringBuilder line = new StringBuilder(weekday(day.path("dayOfWeek").asInt(0)))
                .append(": ").append(text(day.path("title"))).append(", ").append(kind);
        if (day.path("estimatedDurationMinutes").isNumber()) {
            line.append(", ").append(day.path("estimatedDurationMinutes").asInt()).append(" min");
        }
        List<String> segments = new ArrayList<>();
        List<String> moves = new ArrayList<>();
        int moveCount = 0;
        for (JsonNode entry : day.path("entries")) {
            if ("SEGMENT".equals(entry.path("entryType").asText())) {
                segments.add(segmentWords(entry));
            } else {
                moveCount++;
                if (moves.size() < MAX_MOVES_NAMED) {
                    moves.add(moveWords(entry));
                }
            }
        }
        if (!segments.isEmpty()) {
            line.append(": ").append(String.join(", ", segments));
        } else if (moveCount > 0) {
            line.append(", ").append(moveCount).append(moveCount == 1 ? " move: " : " moves: ")
                    .append(String.join(", ", moves));
            if (moveCount > moves.size()) {
                line.append(" and ").append(moveCount - moves.size()).append(" more");
            }
        }
        String effort = effortWords(day.path("effortTarget"));
        if (effort != null) {
            line.append("; ").append(effort);
        }
        return line.append('.').toString();
    }

    private static String segmentWords(JsonNode entry) {
        String kind = text(entry.path("kind"));
        String words = WORDS.getOrDefault(kind, text(entry.path("label"))) + " "
                + duration(entry.path("plannedSeconds").asInt(0));
        int rounds = entry.path("rounds").asInt(1);
        return rounds > 1 ? words + " " + rounds + " times" : words;
    }

    private static String moveWords(JsonNode entry) {
        String name = text(entry.path("exerciseNameSnapshot"));
        String sets = number(entry.path("targetSets"));
        String reps = number(entry.path("targetReps"));
        if (!sets.isEmpty() && !reps.isEmpty()) {
            return name + " " + sets + " x " + reps;
        }
        if (!sets.isEmpty() && entry.path("targetDurationSeconds").isNumber()) {
            return name + " " + sets + " x " + duration(entry.path("targetDurationSeconds").asInt());
        }
        return name;
    }

    private static String effortWords(JsonNode effort) {
        if (!effort.isNumber()) {
            return null;
        }
        int value = effort.asInt();
        if (value <= EASY_EFFORT) {
            return "easy effort";
        }
        if (value <= MODERATE_EFFORT) {
            return "moderate effort";
        }
        return value >= HARD_EFFORT ? "hard effort" : "effort " + value + " of 10";
    }

    /** Seconds as words: 45 s, 5 min, 2 min 30 s. */
    static String duration(int seconds) {
        if (seconds < SECONDS_PER_MINUTE) {
            return seconds + " s";
        }
        int minutes = seconds / SECONDS_PER_MINUTE;
        int rest = seconds % SECONDS_PER_MINUTE;
        return rest == 0 ? minutes + " min" : minutes + " min " + rest + " s";
    }

    private static String weekday(int dayOfWeek) {
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            return "Any day";
        }
        return DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    private static String text(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? "" : node.asText("").strip();
    }

    private static String number(JsonNode node) {
        if (!node.isNumber()) {
            return "";
        }
        return new BigDecimal(node.asText()).stripTrailingZeros().toPlainString();
    }
}
