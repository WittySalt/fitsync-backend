package com.fitsync.workout.messaging;

public enum OutboxEventStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
