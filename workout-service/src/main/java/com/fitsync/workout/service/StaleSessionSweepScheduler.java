package com.fitsync.workout.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the sweep that closes workouts every user left open. */
@Component
@ConditionalOnProperty(prefix = "fitsync.workout.auto-close", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StaleSessionSweepScheduler {

    private final WorkoutService workoutService;

    public StaleSessionSweepScheduler(WorkoutService workoutService) {
        this.workoutService = workoutService;
    }

    // The first run waits one interval, so starting the service does not sweep before it is ready.
    @Scheduled(fixedDelayString = "${fitsync.workout.auto-close.sweep-interval:PT15M}",
            initialDelayString = "${fitsync.workout.auto-close.sweep-interval:PT15M}")
    public void sweep() {
        workoutService.closeStaleSessions();
    }
}
