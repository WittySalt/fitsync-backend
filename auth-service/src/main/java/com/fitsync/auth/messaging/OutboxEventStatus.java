package com.fitsync.backend.messaging;

public enum OutboxEventStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
