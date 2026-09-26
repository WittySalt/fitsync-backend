package com.fitsync.recommendation.coach.orchestrator;

/**
 * Where the orchestrator reports what it is doing.
 *
 * <p>An interface rather than a Spring event so that a turn's progress reaches the one client
 * waiting on it and nobody else. Application events are broadcast, which would mean every open
 * connection filtering by turn identifier, and that is a routing problem invented for no reason
 * when the caller already holds the connection.
 *
 * <p>Implementations are called from the coach's tool pool as well as the turn's own thread, so
 * they must be safe to call from more than one thread at a time. They must also not throw: a
 * disconnected client is not a reason for the turn to fail, because the answer is still being
 * written to the thread and the user will see it when they come back.
 */
@FunctionalInterface
public interface CoachProgressListener {

    /** Used by the non-streaming path, where nobody is watching. */
    CoachProgressListener NONE = event -> {
    };

    void onEvent(CoachProgressEvent event);
}
