package com.fitsync.recommendation.coach.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Where in the app a message was sent from, and which reading (if any) it refers to.
 *
 * <p>This is the whole of what a client is trusted to say about a reading: a page name and an opaque
 * id. It never carries the facts or the reading's text — the server works those out again for the
 * authenticated user and uses them only when {@code readingId} matches what it computes, so nothing
 * a client could invent ever reaches the model's prompt.
 *
 * @param surface   which page the user is on; required whenever a context is sent at all
 * @param readingId the sixteen-character id of the reading the user is asking about, or null when the
 *                  question is not about a specific reading
 * @param recipeId  the id of the recipe page the question was asked on, or null; only the id is
 *                  trusted, the recipe itself is read again by the coach with the user's own credential
 * @param card      the Progress card the question was asked on and the period it showed, or null; only
 *                  its kind, id and dates are trusted, and the card's figures are read again by the coach
 */
@Schema(description = "Where the message was sent from, and the reading (if any) it refers to. "
        + "Never carries reading text or facts, only a page name and an opaque id.")
public record CoachMessageContext(
        @NotNull CoachSurface surface,
        @Pattern(regexp = "^[0-9a-f]{16}$") String readingId,
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$") String recipeId,
        @Valid CoachCardContext card) {

    /** A context without a recipe, which is what clients that predate recipeId send. */
    public CoachMessageContext(CoachSurface surface, String readingId) {
        this(surface, readingId, null, null);
    }

    /** A context without a card, which is what clients that predate card send. */
    public CoachMessageContext(CoachSurface surface, String readingId, String recipeId) {
        this(surface, readingId, recipeId, null);
    }
}
