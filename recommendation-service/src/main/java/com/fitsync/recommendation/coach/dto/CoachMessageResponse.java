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
 */
@Schema(description = "One coach answer with its turn reference and tier.")
public record CoachMessageResponse(
        java.util.UUID threadId,
        java.util.UUID turnId,
        String answer,
        int tier,
        java.util.List<String> toolsUsed,
        int latencyMs,
        String pinnedReadingId) {

    public CoachMessageResponse {
        toolsUsed = toolsUsed == null ? java.util.List.of() : java.util.List.copyOf(toolsUsed);
    }

    @Override
    public java.util.List<String> toolsUsed() {
        return java.util.List.copyOf(toolsUsed);
    }
}
