package com.fitsync.workout.repository;

import java.time.Instant;
import java.util.UUID;

/**
 * What the sets of one session say about its activity: when one was last marked done or skipped,
 * and how many are done. Read with one grouped query instead of loading the sets.
 */
public record SessionActivityRow(UUID sessionId, Instant lastSetAt, Long doneSets) {

    public boolean hasDoneSet() {
        return doneSets != null && doneSets > 0;
    }
}
