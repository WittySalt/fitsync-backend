package com.fitsync.recommendation.coach.tool;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.client.WorkoutCatalogueClient;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import com.fitsync.recommendation.repository.RecommendationContextRepository;
import com.fitsync.recommendation.service.ai.CoachFacts;
import org.springframework.stereotype.Component;

/**
 * The user's computed facts: the last full week's training, today's nutrition, goal pace, logging streak,
 * the last personal record and the weekly food-group split.
 *
 * <p>This is the tool that makes the coach different from a chatbot. Every number in it was worked
 * out by the rule engine from the analytics read model before the model saw the question, so the
 * model phrases rather than calculates. It is also the tool whose absence defines the envelope: if
 * something is not in here and not in a catalogue, the coach does not know it, and saying so is the
 * correct answer.
 *
 * <p>The computed facts are read in-process, because they belong to this service. The training
 * counts among them describe the last full week, which is the week the weekly recap covered. A
 * question about "my week" is about the current one, so the tool also asks workout-service for the
 * current week so far and hands both over, each under its own name. When workout-service cannot be
 * reached the current week is left out and the rest is returned as it is.
 */
@Component
public class ReadFactsTool implements CoachTool {

    private final RecommendationContextRepository contextRepository;
    private final CoachFacts coachFacts;
    private final ObjectMapper objectMapper;
    private final WorkoutCatalogueClient workoutClient;

    public ReadFactsTool(RecommendationContextRepository contextRepository,
                         CoachFacts coachFacts,
                         ObjectMapper objectMapper,
                         WorkoutCatalogueClient workoutClient) {
        this.contextRepository = contextRepository;
        this.coachFacts = coachFacts;
        this.objectMapper = objectMapper;
        this.workoutClient = workoutClient;
    }

    /** The name of the group that holds the current week so far. */
    public static final String CURRENT_WEEK_GROUP = "trainingThisWeek";

    static final String CURRENT_WEEK_PERIOD = "CURRENT_WEEK_SO_FAR";

    private static final String DEFAULT_TIMEZONE = "UTC";

    @Override
    public String name() {
        return "readFacts";
    }

    private Optional<Map<String, Object>> currentWeek(CoachCallContext context, String timezone) {
        String zone = timezone == null || timezone.isBlank() ? DEFAULT_TIMEZONE : timezone;
        try {
            String body = workoutClient.weeklyInsight(context.bearerToken(), zone);
            if (body == null) {
                return Optional.empty();
            }
            JsonNode week = objectMapper.readTree(body);
            if (!week.path("completedSessions").isInt() || !week.path("totalMinutes").isInt()) {
                return Optional.empty();
            }
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("period", CURRENT_WEEK_PERIOD);
            if (week.path("weekStart").isTextual()) {
                group.put("weekStart", week.path("weekStart").asText());
            }
            group.put("sessions", week.path("completedSessions").asInt());
            group.put("totalMinutes", week.path("totalMinutes").asInt());
            return Optional.of(group);
        } catch (JsonProcessingException | RuntimeException ex) {
            // The current week is an addition. A turn is still answered from the rest of the facts.
            return Optional.empty();
        }
    }

    @Override
    public ToolClass toolClass() {
        return ToolClass.READ;
    }

    @Override
    public String promptSignature() {
        return """
                readFacts()
                  no arguments
                  returns the user's computed facts as JSON: training, nutritionToday, goalPace,
                  loggingStreak, personalRecord, foodGroupWeek, focusAreas. Every number here is
                  already calculated — quote it, never recompute it. The training group describes
                  the last full week, Monday to Sunday. The trainingThisWeek group describes the
                  current week so far. Say which of the two weeks a count belongs to.""";
    }

    @Override
    public ToolResult invoke(CoachCallContext context, JsonNode args) {
        Optional<RecommendationContextEntity> entity = contextRepository.findById(context.userId());
        Map<String, Object> facts = new LinkedHashMap<>(entity
                .map(found -> coachFacts.assemble(found, null))
                .orElse(Map.of()));

        if (facts.isEmpty()) {
            return ToolResult.of("no facts yet",
                    "This user has no computed facts yet, which means they have not logged enough "
                            + "for the rules to fire. Encourage a first workout or meal rather than "
                            + "inventing numbers.");
        }

        currentWeek(context, entity.map(RecommendationContextEntity::getTimezone).orElse(null))
                .ifPresent(week -> facts.put(CURRENT_WEEK_GROUP, week));

        try {
            return ToolResult.of(facts.size() + " fact groups",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(facts));
        } catch (JsonProcessingException ex) {
            return ToolResult.of("facts unreadable",
                    "The computed facts could not be serialised. Say that you cannot see them.");
        }
    }
}
