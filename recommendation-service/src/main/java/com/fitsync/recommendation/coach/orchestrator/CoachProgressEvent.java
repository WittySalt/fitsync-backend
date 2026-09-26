package com.fitsync.recommendation.coach.orchestrator;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One thing the coach did, published while the turn is still running.
 *
 * <p>The streaming design deliberately carries progress rather than tokens. Token streaming would
 * mean rebuilding the gateway, which forwards a whole response body today, and for an assistant
 * that spends most of a turn reading data rather than writing prose it is also the less useful of
 * the two: seeing that it checked your last four sessions before answering says more than watching
 * the answer appear one word at a time.
 *
 * <p>These are the same facts the stored trace holds, so the live view and the expandable trace
 * under a finished answer render from one shape.
 *
 * @param kind      what happened
 * @param tool      the tool this event concerns, absent on {@link Kind#THINKING}
 * @param summary   the tool's result in outline, for example "10 candidates"
 * @param latencyMs how long the tool took, present once it has finished
 */
@Schema(description = "A step the coach took, streamed while the turn is still running.")
public record CoachProgressEvent(Kind kind, String tool, String summary, Integer latencyMs) {

    public enum Kind {
        /** A tool has been dispatched. Several may be open at once on the planned path. */
        TOOL_STARTED,
        /** A tool has come back, with its latency and a one-line summary of what it found. */
        TOOL_FINISHED,
        /** The model is being asked for its next action or for the answer. */
        THINKING
    }

    public static CoachProgressEvent started(String tool) {
        return new CoachProgressEvent(Kind.TOOL_STARTED, tool, null, null);
    }

    public static CoachProgressEvent finished(String tool, String summary, int latencyMs) {
        return new CoachProgressEvent(Kind.TOOL_FINISHED, tool, summary, Integer.valueOf(latencyMs));
    }

    public static CoachProgressEvent thinking() {
        return new CoachProgressEvent(Kind.THINKING, null, null, null);
    }
}
