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
public record CoachOutcome(String answer,
                           int tier,
                           List<String> toolsUsed,
                           int latencyMs,
                           boolean modelAvailable,
                           String guardrailVerdict) {

    /** The verdict recorded on a turn a model was allowed to answer. */
    public static final String VERDICT_PASSED = "PASSED";
    /** The verdict recorded when the AI tier was off and no model was consulted. */
    public static final String VERDICT_MODEL_OFF = "MODEL_OFF";
    /** Prefix for a refusal, followed by the rule that fired, so the harness can count each rule. */
    public static final String VERDICT_REFUSED_PREFIX = "REFUSED_";

    public CoachOutcome {
        toolsUsed = toolsUsed == null ? List.of() : List.copyOf(toolsUsed);
    }

    @Override
    public List<String> toolsUsed() {
        return List.copyOf(toolsUsed);
    }

    static CoachOutcome answered(String answer, List<String> toolsUsed, int latencyMs) {
        return new CoachOutcome(answer, CoachOrchestrator.TIER_LOCAL_MODEL, toolsUsed, latencyMs, true, VERDICT_PASSED);
    }

    static CoachOutcome unavailable(String message) {
        return new CoachOutcome(message, CoachOrchestrator.TIER_UNAVAILABLE, List.of(), 0, false, VERDICT_MODEL_OFF);
    }

    /**
     * A guardrail answered instead of a model. Tier 0, because a rule decided; the model is still
     * available, it was simply not asked.
     */
    static CoachOutcome refused(String rule, String message, int latencyMs) {
        return new CoachOutcome(message, CoachOrchestrator.TIER_RULE, List.of(), latencyMs, true,
                VERDICT_REFUSED_PREFIX + rule);
    }

    public boolean refused() {
        return guardrailVerdict != null && guardrailVerdict.startsWith(VERDICT_REFUSED_PREFIX);
    }
}
