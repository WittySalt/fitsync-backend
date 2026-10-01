package com.fitsync.workout.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for closing workouts that were left open.
 *
 * @param enabled       whether the scheduled sweep runs; the check for the calling user does not depend on it
 * @param idle          how long a workout may sit without a set being logged before it counts as left
 * @param sweepInterval the delay between two sweeps
 */
@ConfigurationProperties(prefix = "fitsync.workout.auto-close")
public record AutoCloseProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("PT3H") Duration idle,
        @DefaultValue("PT15M") Duration sweepInterval) {
}
