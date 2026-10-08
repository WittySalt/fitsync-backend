package com.fitsync.recommendation.coach.tool;

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
 * Exercise catalogue retrieval, 874 rows behind a structured filter.
 *
 * <p>The observation handed back to the model is a projection, not the response. A catalogue row
 * carries instructions, secondary muscles, images and audit columns; a 3-billion-parameter model
 * given twenty of those in full would spend its context on material it cannot use and lose the
 * facts it was grounded with. Twenty lines of {@code id | name | muscle | equipment} is everything
 * needed to choose, and it keeps the exercise identifiers verbatim so a draft can reference them.
 */
@Component
public class SearchExercisesTool implements CoachTool {

    /** Enough to choose from without crowding out the grounded facts. */
    private static final int PAGE_SIZE = 20;

    private final WorkoutCatalogueClient client;
    private final ObjectMapper objectMapper;

    public SearchExercisesTool(WorkoutCatalogueClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "searchExercises";
    }

    @Override
    public ToolClass toolClass() {
        return ToolClass.READ;
    }

    @Override
    public String promptSignature() {
        return """
                searchExercises(muscleGroup?, equipment?, difficulty?, query?)
                  muscleGroup: CHEST BACK SHOULDERS ARMS LEGS GLUTES CORE FULL_BODY CARDIO
                  equipment:   BARBELL DUMBBELL CABLE MACHINE BODYWEIGHT KETTLEBELL BANDS OTHER
                  difficulty:  BEGINNER INTERMEDIATE ADVANCED
                  query:       words of the name; for the user's own lift, the exact name their records give
                  returns up to 20 lines of "id | name | muscle | equipment\"""";
    }

    @Override
    public ToolResult invoke(CoachCallContext context, JsonNode args) {
        String muscle = upper(args.path("muscleGroup").asText(null));
        String equipment = upper(args.path("equipment").asText(null));
        String difficulty = upper(args.path("difficulty").asText(null));
        String query = blankToNull(args.path("query").asText(null));

        String body = client.searchExercises(
                context.bearerToken(), muscle, equipment, difficulty, query, PAGE_SIZE);
        if (body == null) {
            return ToolResult.of("catalogue unavailable",
                    "The exercise catalogue could not be reached. Answer without it, or say so.");
        }

        List<String> rows = project(body);
        if (rows.isEmpty()) {
            return ToolResult.of("0 candidates",
                    "No exercise matched those filters. Try a broader muscle group or drop the equipment filter.");
        }
        return ToolResult.of(rows.size() + " candidates", String.join("\n", rows));
    }

    /** Narrow each catalogue row to the four fields a choice actually turns on. */
    private List<String> project(String body) {
        List<String> rows = new ArrayList<>();
        try {
            JsonNode content = objectMapper.readTree(body).path("content");
            for (JsonNode item : content) {
                rows.add("%s | %s | %s | %s".formatted(
                        item.path("exerciseId").asText(),
                        item.path("name").asText(),
                        item.path("primaryMuscleGroup").asText(""),
                        item.path("equipment").asText("")));
            }
        } catch (JsonProcessingException ex) {
            // A catalogue that answered with something unparseable is a missing result, not a
            // failed turn: the model is told there were no candidates and works around it.
            return List.of();
        }
        return rows;
    }

    private static String upper(String value) {
        String trimmed = blankToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
