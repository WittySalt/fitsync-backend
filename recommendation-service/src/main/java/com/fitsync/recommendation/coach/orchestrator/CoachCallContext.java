package com.fitsync.recommendation.coach.orchestrator;

import java.util.UUID;

/**
 * Everything a turn needs to act on the caller's behalf, passed explicitly rather than read from a
 * thread-local.
 *
 * <p>That is a deliberate choice. The orchestrator runs tools concurrently and, from day three,
 * streams its progress from a separate thread; Spring's security context does not follow either.
 * Carrying the caller's identity and token as an argument makes the data flow visible and removes
 * a whole class of "works until it is made asynchronous" bug.
 *
 * <p>{@code bearerToken} is the caller's own token, forwarded unchanged to the services that own
 * the catalogues. The coach therefore reads with exactly the caller's authority and no more, which
 * matters because those catalogues contain per-owner rows — a user's custom exercises and foods are
 * visible to them and should be visible to their coach, and to nobody else. It is never logged.
 */
public record CoachCallContext(UUID userId, String bearerToken, UUID turnId) {

    public CoachCallContext withTurn(UUID newTurnId) {
        return new CoachCallContext(userId, bearerToken, newTurnId);
    }

    /** Guards against the token reaching a log line or an error message by accident. */
    @Override
    public String toString() {
        return "CoachCallContext[userId=" + userId + ", turnId=" + turnId + ", token=redacted]";
    }
}
