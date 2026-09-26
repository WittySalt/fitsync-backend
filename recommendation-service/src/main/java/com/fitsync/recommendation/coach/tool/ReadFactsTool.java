package com.fitsync.recommendation.coach.tool;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import com.fitsync.recommendation.repository.RecommendationContextRepository;
import com.fitsync.recommendation.service.ai.CoachFacts;
import org.springframework.stereotype.Component;

/**
 * The user's computed facts: this week's training, today's nutrition, goal pace, logging streak,
 * the last personal record and the weekly food-group split.
 *
 * <p>This is the tool that makes the coach different from a chatbot. Every number in it was worked
 * out by the rule engine from the analytics read model before the model saw the question, so the
 * model phrases rather than calculates. It is also the tool whose absence defines the envelope: if
 * something is not in here and not in a catalogue, the coach does not know it, and saying so is the
 * correct answer.
 *
 * <p>In-process, unlike the catalogue tools, because the facts belong to this service. A few
 * milliseconds, no network.
 */
@Component
public class ReadFactsTool implements CoachTool {

    private final RecommendationContextRepository contextRepository;
    private final CoachFacts coachFacts;
    private final ObjectMapper objectMapper;

    public ReadFactsTool(RecommendationContextRepository contextRepository,
                         CoachFacts coachFacts,
                         ObjectMapper objectMapper) {
        this.contextRepository = contextRepository;
        this.coachFacts = coachFacts;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "readFacts";
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
                  already calculated — quote it, never recompute it.""";
    }

    @Override
    public ToolResult invoke(CoachCallContext context, JsonNode args) {
        Map<String, Object> facts = contextRepository.findById(context.userId())
                .map(entity -> coachFacts.assemble(entity, null))
                .orElse(Map.of());

        if (facts.isEmpty()) {
            return ToolResult.of("no facts yet",
                    "This user has no computed facts yet, which means they have not logged enough "
                            + "for the rules to fire. Encourage a first workout or meal rather than "
                            + "inventing numbers.");
        }

        try {
            return ToolResult.of(facts.size() + " fact groups",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(facts));
        } catch (JsonProcessingException ex) {
            return ToolResult.of("facts unreadable",
                    "The computed facts could not be serialised. Say that you cannot see them.");
        }
    }
}
