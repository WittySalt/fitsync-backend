package com.fitsync.recommendation.coach.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The answer, plus the turn it was written to so a client can fetch its trace.
 *
 * <p>{@code tier} is 2 when a model answered and -1 when the AI tier is switched off, in which case
 * {@code answer} explains that rather than failing. Rule and template tiers are recorded on the
 * turn by the paths that take them.
 *
 * <p>{@code pinnedReadingId} is set only when the request carried a reading id that the server
 * actually resolved and handed to the model; the client draws citation badges on the answer only
 * when this is set, never merely because the request asked for one.
 *
 * <p>{@code status} is {@code ANSWERED} for every answer. It is {@code RUNNING} only when a retry
 * carrying the id of a turn that is still being worked on gave up waiting; the answer is then empty
 * and the turn can be read from the thread once it finishes.
 */
@Schema(description = "One coach answer with its turn reference and tier.")
public record CoachMessageResponse(
        java.util.UUID threadId,
        java.util.UUID turnId,
        String answer,
        int tier,
        java.util.List<String> toolsUsed,
        int latencyMs,
        String pinnedReadingId,
        String status) {

    public static final String ANSWERED = "ANSWERED";
    public static final String RUNNING = "RUNNING";

    /** A finished answer. */
    public CoachMessageResponse(java.util.UUID threadId, java.util.UUID turnId, String answer, int tier,
                                java.util.List<String> toolsUsed, int latencyMs, String pinnedReadingId) {
        this(threadId, turnId, answer, tier, toolsUsed, latencyMs, pinnedReadingId, ANSWERED);
    }

    public CoachMessageResponse {
        status = status == null ? ANSWERED : status;
        toolsUsed = toolsUsed == null ? java.util.List.of() : java.util.List.copyOf(toolsUsed);
    }

    @Override
    public java.util.List<String> toolsUsed() {
        return java.util.List.copyOf(toolsUsed);
    }
}
