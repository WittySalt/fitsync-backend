package com.fitsync.recommendation.coach.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;

/**
 * One capability the orchestrator can invoke on the model's behalf.
 *
 * <p>A tool declares itself in {@link #promptSignature()} rather than having its description
 * written out again in a prompt template. Keeping the two together means a tool cannot be added
 * without the model learning about it, or changed without the description following.
 */
public interface CoachTool {

    /** The identifier the model emits. Stable; it is persisted in the audit trail. */
    String name();

    ToolClass toolClass();

    /**
     * One line, as the model sees it: the call shape and what it returns. Written for a small model,
     * so it is terse and concrete rather than discursive.
     */
    String promptSignature();

    /**
     * @param args the model's arguments, already parsed. Never null; an empty object when the model
     *             supplied none.
     * @throws IllegalArgumentException when the arguments do not make sense, which the orchestrator
     *                                  turns into an observation the model can correct from rather
     *                                  than a failed turn.
     */
    ToolResult invoke(CoachCallContext context, JsonNode args);
}
