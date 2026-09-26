package com.fitsync.recommendation.coach.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One tool call made while answering a turn. Rendered in the trace view.")
public record ToolInvocationView(
        int seq,
        String tool,
        String args,
        String resultSummary,
        int latencyMs,
        boolean ok,
        String error) {
}
