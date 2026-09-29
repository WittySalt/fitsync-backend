package com.fitsync.recommendation.coach.dto;

/**
 * Which page of the app a coach turn was started from.
 *
 * <p>Sent by the client as part of {@link CoachMessageContext} so the coach can open its answer with
 * the same context a human helper would already have ("you're looking at your workout page"), and so
 * a pinned reading can only ever be resolved for the surface that actually shows one. The client
 * sends this value alone; it never sends the reading text itself.
 */
public enum CoachSurface {
    HOME,
    WORKOUT,
    NUTRITION,
    PROGRESS,
    PROFILE
}
