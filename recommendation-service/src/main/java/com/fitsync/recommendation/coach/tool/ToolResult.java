package com.fitsync.recommendation.coach.tool;

/**
 * What a tool hands back.
 *
 * <p>{@code summary} is what the trace shows and what the audit row stores — "20 candidates", "12
 * facts". {@code observation} is what goes back to the model. They are separate because an audit
 * table has no business holding twenty catalogue rows, and because a human reading a trace wants
 * the shape of the answer rather than its contents.
 */
public record ToolResult(String summary, String observation) {

    public static ToolResult of(String summary, String observation) {
        return new ToolResult(summary, observation);
    }
}
