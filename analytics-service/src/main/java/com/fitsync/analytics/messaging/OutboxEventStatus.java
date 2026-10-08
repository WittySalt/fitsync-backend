package com.fitsync.analytics.messaging;

/** Lifecycle of an outbox row: written as PENDING, then PUBLISHED or FAILED by the relay. */
public enum OutboxEventStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
