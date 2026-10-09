package com.fitsync.persistence.outbox;

/**
 * The column that holds an outbox event's publish time. Services built on {@link BaseOutboxEventEntity} use
 * {@code date_time_published}; auth-service predates it and uses {@code published_at}. {@link OutboxCleanupJob}
 * keeps one fixed delete statement per value, so no column name is ever pieced into SQL at run time.
 */
public enum OutboxPublishedColumn {
    DATE_TIME_PUBLISHED,
    PUBLISHED_AT
}
