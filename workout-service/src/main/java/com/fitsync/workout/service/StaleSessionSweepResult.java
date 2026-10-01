package com.fitsync.workout.service;

/**
 * What one sweep for left workouts did. The counts are all there is to report: they say how many
 * sessions were completed from their logged sets, how many were cancelled for having none, and how
 * many could not be closed and are left for the next run.
 */
public record StaleSessionSweepResult(int completed, int cancelled, int failed) {

    public int closed() {
        return completed + cancelled;
    }
}
