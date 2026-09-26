package com.fitsync.recommendation.coach.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One turn, with the telemetry the trace view renders underneath it.
 *
 * <p>{@code tier} is 0 rule, 1 template, 2 local model, 3 frontier model. A tier of 0 or 1 means
 * no model was called, which is the common case and the thing the tiering story rests on.
 */
@Schema(description = "One turn in a coach thread, with its trace.")
public record CoachTurnView(
        UUID turnId,
        int seq,
        String role,
        String content,
        Integer tier,
        String model,
        Integer promptTokens,
        Integer completionTokens,
        Integer latencyMs,
        String guardrailVerdict,
        List<ToolInvocationView> tools,
        Instant createdAt) {

    public CoachTurnView {
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    @Override
    public List<ToolInvocationView> tools() {
        return List.copyOf(tools);
    }
}
