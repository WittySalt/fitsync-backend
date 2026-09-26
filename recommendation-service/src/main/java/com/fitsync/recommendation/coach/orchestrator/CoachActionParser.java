package com.fitsync.recommendation.coach.orchestrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Turns the model's reply into an action.
 *
 * <p>The day-zero spike measured 100% well-formed JSON from {@code llama3.2:3b} over ninety trials,
 * so the strict path is the expected one. The tolerance below exists because a model that is right
 * ninety-nine times in a hundred still fails in front of an assessor once an hour, and the two
 * failure modes it forgives — a markdown fence, and prose wrapped around the object — are the ones
 * small models actually produce. Anything else is reported as unparseable so the orchestrator can
 * ask again rather than guess.
 */
@Component
public class CoachActionParser {

    private final ObjectMapper objectMapper;

    public CoachActionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CoachAction parse(String reply) {
        if (reply == null || reply.isBlank()) {
            return CoachAction.unparseable();
        }
        JsonNode node = readJson(stripFence(reply.strip()));
        if (node == null || !node.isObject()) {
            return CoachAction.unparseable();
        }

        String answer = node.path("answer").asText(null);
        if (answer != null && !answer.isBlank()) {
            return CoachAction.answer(answer.strip());
        }

        String tool = node.path("tool").asText(null);
        if (tool != null && !tool.isBlank()) {
            JsonNode args = node.path("args");
            return CoachAction.tool(tool.strip(), args.isObject() ? args : objectMapper.createObjectNode());
        }
        return CoachAction.unparseable();
    }

    private static String stripFence(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        int firstNewline = text.indexOf('\n');
        int closing = text.lastIndexOf("```");
        if (firstNewline < 0 || closing <= firstNewline) {
            return text;
        }
        return text.substring(firstNewline + 1, closing).strip();
    }

    /**
     * Reads the text as JSON, and failing that, the outermost {@code { ... }} inside it. The second
     * attempt covers a model that prefixed its object with a sentence of explanation.
     */
    private JsonNode readJson(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (JsonProcessingException first) {
            int open = text.indexOf('{');
            int close = text.lastIndexOf('}');
            if (open < 0 || close <= open) {
                return null;
            }
            try {
                return objectMapper.readTree(text.substring(open, close + 1));
            } catch (JsonProcessingException second) {
                return null;
            }
        }
    }
}
