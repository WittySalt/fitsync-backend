package com.fitsync.workout.dto;

/** The workout the person has in progress, with the progress through it a client needs to reopen it. */
public record ActiveWorkoutSessionView(
        WorkoutSessionView session,
        WorkoutSessionResumeView resume) {
}
