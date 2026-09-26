package com.fitsync.recommendation.coach.tool;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
 */
@Component
public class ReadRecentTrainingTool implements CoachTool {

    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 90;
    private static final int PAGE_SIZE = 20;

    private final WorkoutCatalogueClient client;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ReadRecentTrainingTool(WorkoutCatalogueClient client, ObjectMapper objectMapper, Clock clock) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.clock = clock;
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
                  returns one line per completed session: "date | name | type | minutes\"""";
    }

    @Override
    public ToolResult invoke(CoachCallContext context, JsonNode args) {
        int days = args.path("days").asInt(DEFAULT_DAYS);
        days = Math.max(1, Math.min(days, MAX_DAYS));
        LocalDate from = LocalDate.now(clock).minusDays(days - 1L);

        String body = client.completedSessions(context.bearerToken(), from, PAGE_SIZE);
        if (body == null) {
            return ToolResult.of("history unavailable",
                    "Recent training history could not be read. Answer without it, or say so.");
        }

        List<String> rows = project(body);
        if (rows.isEmpty()) {
            return ToolResult.of("0 sessions",
                    "No completed sessions in the last " + days + " days.");
        }
        return ToolResult.of(rows.size() + " sessions in " + days + "d", String.join("\n", rows));
    }

    private List<String> project(String body) {
        List<String> rows = new ArrayList<>();
        try {
            JsonNode content = objectMapper.readTree(body).path("content");
            for (JsonNode item : content) {
                String completedAt = item.path("completedAt").asText("");
                rows.add("%s | %s | %s | %s min".formatted(
                        completedAt.length() >= 10 ? completedAt.substring(0, 10) : completedAt,
                        item.path("name").asText(""),
                        item.path("workoutType").asText(""),
                        item.path("durationMinutes").asText("?")));
            }
        } catch (JsonProcessingException ex) {
            // A catalogue that answered with something unparseable is a missing result, not a
            // failed turn: the model is told there were no candidates and works around it.
            return List.of();
        }
        return rows;
    }
}
