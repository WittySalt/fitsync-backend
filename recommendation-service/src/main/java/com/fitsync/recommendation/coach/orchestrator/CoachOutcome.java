package com.fitsync.recommendation.coach.orchestrator;

import java.util.List;

/**
 * What one turn produced, with enough detail for the trace to render it.
 *
 * <p>{@code tier} is the thing worth reading: {@link CoachOrchestrator#TIER_LOCAL_MODEL} means a
 * model was called, and {@link CoachOrchestrator#TIER_UNAVAILABLE} means one could not be. Tiers 0
 * and 1, where the rules and templates answer without any model at all, are recorded by the callers
 * that take that path.
 */
public record CoachOutcome(String answer, int tier, List<String> toolsUsed, int latencyMs, boolean modelAvailable) {

    public CoachOutcome {
        toolsUsed = toolsUsed == null ? List.of() : List.copyOf(toolsUsed);
    }

    @Override
    public List<String> toolsUsed() {
        return List.copyOf(toolsUsed);
    }

    static CoachOutcome answered(String answer, List<String> toolsUsed, int latencyMs) {
        return new CoachOutcome(answer, CoachOrchestrator.TIER_LOCAL_MODEL, toolsUsed, latencyMs, true);
    }

    static CoachOutcome unavailable(String message) {
        return new CoachOutcome(message, CoachOrchestrator.TIER_UNAVAILABLE, List.of(), 0, false);
    }
}
