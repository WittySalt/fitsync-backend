package com.fitsync.recommendation.coach.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A conversation as it appears in the history list.
 *
 * @param title         the opening question cut to 60 characters on a word boundary, or the title the
 *                      thread was created with
 * @param surface       the page the thread was started from (HOME, WORKOUT, NUTRITION, PROGRESS,
 *                      PROFILE), or null when it was started without one
 * @param lastMessageAt when the thread last had a message; the list is ordered by it, newest first
 */
@Schema(description = "A conversation with the coach, as it appears in the thread list.")
public record CoachThreadView(
        UUID threadId,
        String title,
        int turnCount,
        Instant lastActiveAt,
        Instant createdAt,
        String surface,
        Instant lastMessageAt) {

    /** A view without a surface, which is what threads that predate the field have. */
    public CoachThreadView(UUID threadId, String title, int turnCount, Instant lastActiveAt, Instant createdAt) {
        this(threadId, title, turnCount, lastActiveAt, createdAt, null, lastActiveAt);
    }
}
