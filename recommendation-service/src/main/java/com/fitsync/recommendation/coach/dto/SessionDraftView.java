package com.fitsync.recommendation.coach.dto;

import java.util.UUID;

import com.fitsync.recommendation.coach.draft.SessionDraft;
import com.fitsync.recommendation.coach.entity.ArtifactStatus;
import com.fitsync.recommendation.coach.entity.DraftSource;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A draft as a client sees it.
 *
 * <p>The source is part of the contract rather than an internal detail. A session the rules built
 * is plainer than one the model wrote, and the interface says so instead of passing it off as the
 * same thing; a user who is told why a suggestion looks sparse trusts the ones that do not.
 *
 * @param validationAttempts 1 when the model was right first time, 2 when it needed correcting,
 *                           0 when no model was involved
 * @param planId             the plan this became once accepted, otherwise null
 * @param sessionId          the session started from that plan, otherwise null; a client opens
 *                           this directly rather than searching the Workout page for it
 * @param rejectionReason    why the user turned it down, when they said
 */
@Schema(description = "A proposed session, with how it was produced.")
public record SessionDraftView(
        UUID artifactId,
        ArtifactStatus status,
        DraftSource source,
        int validationAttempts,
        SessionDraft session,
        UUID planId,
        UUID sessionId,
        String rejectionReason) {
}
