package com.fitsync.recommendation.coach.tool;

import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.MAX_ITEMS;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.line;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.number;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.summaryLines;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.text;
import static com.fitsync.recommendation.coach.tool.ProgressToolSupport.words;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.client.WorkoutCatalogueClient;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import org.springframework.stereotype.Component;

/**
 * One exercise's page for a period, read from workout-service: the estimated one-rep max and its
 * change, the heaviest set, the best volume, the records and the latest sessions.
 */
@Component
public class ReadExerciseProgressTool extends ProgressReadTool {

    private final WorkoutCatalogueClient client;

    public ReadExerciseProgressTool(WorkoutCatalogueClient client, ObjectMapper objectMapper, UserCalendar calendar) {
        super(objectMapper, calendar);
        this.client = client;
    }

    @Override
    public String name() {
        return "readExerciseProgress";
    }

    @Override
    public String promptSignature() {
        return """
                readExerciseProgress(exerciseId, from?, to?)
                  exerciseId: the exercise's id, a UUID; from, to: dates, at most 370 days apart, default the
                  last 28 days
                  returns that exercise's page: estimated one-rep max and its change, heaviest set, best
                  volume, records, latest sessions
                  For the user's own lift, use the exercise their records name (a record in readFacts or
                  readTrainingProgress), found with searchExercises by that exact name. A similar exercise
                  from the catalogue, such as a barbell press for a dumbbell one, holds none of their sessions.""";
    }

    @Override
    protected String subject() {
        return "Exercise progress";
    }

    @Override
    protected String fetch(CoachCallContext context, JsonNode args, ProgressToolSupport.Period period) {
        UUID exerciseId;
        try {
            exerciseId = UUID.fromString(args.path("exerciseId").asText("").strip());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("exerciseId must be the exercise's id, a UUID", ex);
        }
        return client.exerciseProgress(context.bearerToken(), context.timezone(), exerciseId,
                period.from(), period.to());
    }

    @Override
    protected String render(JsonNode body, ProgressToolSupport.Period period) {
        StringBuilder out = new StringBuilder();
        String name = text(body, "name");
        String group = words(body.path("primaryGroup").asText(""));
        String sessions = number(body.path("sessions"));
        if (!name.isEmpty()) {
            line(out, "Exercise: " + name + (group.isEmpty() ? "" : ", trains " + group) + ".");
        }
        String firstSeen = body.path("firstSeen").asText("");
        if (sessions != null) {
            line(out, "Sessions with it: " + sessions
                    + (firstSeen.isBlank() ? "" : ", first logged on " + ProgressToolSupport.clean(firstSeen)) + ".");
        }
        JsonNode tiles = body.path("tiles");
        String e1rm = number(tiles.path("e1rm").path("value"));
        if (e1rm != null) {
            String change = ProgressToolSupport.absolute(tiles.path("e1rm").path("changeKg"));
            int direction = ProgressToolSupport.sign(tiles.path("e1rm").path("changeKg"));
            String since = tiles.path("e1rm").path("since").asText("");
            line(out, "Estimated one-rep max (weight times one plus reps over 30, from the heaviest set): " + e1rm + " kg"
                    + (change == null || direction == 0 ? ""
                    : ", " + change + " kg " + (direction < 0 ? "down" : "up")
                    + (since.isBlank() ? "" : " since " + ProgressToolSupport.clean(since))) + ".");
        }
        JsonNode heaviest = tiles.path("heaviestSet");
        String weight = number(heaviest.path("weightKg"));
        String reps = number(heaviest.path("reps"));
        if (weight != null && reps != null) {
            String date = heaviest.path("date").asText("");
            line(out, "Heaviest set: " + weight + " kg for " + reps + " reps"
                    + (date.isBlank() ? "" : " on " + ProgressToolSupport.clean(date)) + ".");
        }
        String volume = number(tiles.path("bestVolume").path("kg"));
        if (volume != null) {
            String date = tiles.path("bestVolume").path("date").asText("");
            line(out, "Best session volume: " + volume + " kg" + (date.isBlank() ? "" : " on " + ProgressToolSupport.clean(date)) + ".");
        }
        String records = number(tiles.path("records"));
        if (records != null) {
            line(out, "Records: " + records + ".");
        }
        appendHistory(out, body.path("history"));
        List<String> summary = summaryLines(body.path("summary"));
        if (!summary.isEmpty()) {
            line(out, "Summary: " + String.join("; ", summary) + ".");
        }
        if (out.length() == 0) {
            return "";
        }
        return "Exercise from " + period.describe() + ".\n" + out;
    }

    private static void appendHistory(StringBuilder out, JsonNode history) {
        List<String> lines = new ArrayList<>();
        int start = Math.max(0, history.size() - MAX_ITEMS);
        for (int i = start; i < history.size(); i++) {
            JsonNode session = history.get(i);
            String date = session.path("date").asText("");
            List<String> sets = new ArrayList<>();
            for (JsonNode set : session.path("sets")) {
                String weight = number(set.path("weightKg"));
                String reps = number(set.path("reps"));
                if (weight != null && reps != null) {
                    sets.add(weight + " kg x " + reps);
                }
            }
            if (date.isBlank() || sets.isEmpty()) {
                continue;
            }
            lines.add("- " + ProgressToolSupport.clean(date) + ": " + String.join(", ", sets)
                    + (session.path("record").asBoolean(false) ? ", a record" : ""));
        }
        if (!lines.isEmpty()) {
            line(out, "Latest sessions:");
            lines.forEach(session -> line(out, session));
        }
    }
}
