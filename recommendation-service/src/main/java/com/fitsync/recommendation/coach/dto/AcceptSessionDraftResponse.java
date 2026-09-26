package com.fitsync.recommendation.coach.dto;

import java.util.UUID;

import com.fitsync.recommendation.coach.entity.ArtifactStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What accepting a draft produced.
 *
 * <p>The plan is always present once this returns. The session is present when workout-service
 * started one, which is the normal case, and null when the plan was saved but could not be
 * started; the response says which, so a client can open the session directly or fall back to the
 * plan page rather than guessing.
 */
@Schema(description = "The plan and session an accepted draft became.")
public record AcceptSessionDraftResponse(
        UUID artifactId,
        ArtifactStatus status,
        UUID planId,
        UUID sessionId,
        boolean sessionStarted) {
}
