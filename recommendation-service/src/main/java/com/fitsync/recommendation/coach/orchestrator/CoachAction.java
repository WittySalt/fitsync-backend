package com.fitsync.recommendation.coach.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One step the model asked for: either call a tool, or answer.
 *
 * <p>Asking for a single action at a time, rather than letting a provider's tool-calling drive the
 * exchange, is what lets the same loop run on the Claude API and on a 3-billion-parameter local
 * model. A malformed action is then a parse failure we retry, not an exception from somebody else's
 * client.
 */
public record CoachAction(Kind kind, String tool, JsonNode args, String answer) {

    public enum Kind { TOOL, ANSWER, UNPARSEABLE }

    public static CoachAction tool(String tool, JsonNode args) {
        return new CoachAction(Kind.TOOL, tool, args, null);
    }

    public static CoachAction answer(String answer) {
        return new CoachAction(Kind.ANSWER, null, null, answer);
    }

    public static CoachAction unparseable() {
        return new CoachAction(Kind.UNPARSEABLE, null, null, null);
    }
}
