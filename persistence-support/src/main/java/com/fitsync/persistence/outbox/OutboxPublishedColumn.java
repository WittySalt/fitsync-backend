package com.fitsync.persistence.outbox;

/**
 * The column that holds an outbox event's publish time. Services built on {@link BaseOutboxEventEntity} use
 * {@code date_time_published}; auth-service predates it and uses {@code published_at}. Each value carries its
 * own fixed delete statement, so no column name is ever pieced into SQL at run time.
 */
public enum OutboxPublishedColumn {

    DATE_TIME_PUBLISHED("""
            delete from outbox_events
            where event_id in (
                select event_id from outbox_events
                where status = ? and date_time_published is not null and date_time_published < ?
                order by date_time_published
                limit ?)"""),

    PUBLISHED_AT("""
            delete from outbox_events
            where event_id in (
                select event_id from outbox_events
                where status = ? and published_at is not null and published_at < ?
                order by published_at
                limit ?)""");

    private final String deleteBatchSql;

    OutboxPublishedColumn(String deleteBatchSql) {
        this.deleteBatchSql = deleteBatchSql;
    }

    String deleteBatchSql() {
        return deleteBatchSql;
    }
}
