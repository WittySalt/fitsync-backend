package com.fitsync.persistence.outbox;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Deletes outbox events that were PUBLISHED longer ago than {@link OutboxCleanupProperties#retention()}.
 *
 * <p>Only rows whose status is exactly {@code PUBLISHED} and whose publish time is before the cut-off are
 * removed. PENDING and FAILED rows are never touched, so an event that has not reached Kafka cannot be lost.
 * Rows go in batches of {@code batchSize}, each its own short statement and commit. The statement is one of two
 * constant strings and every value is a bind parameter. Several instances may run it at once: each statement
 * deletes whatever matches at that moment, so a row is deleted once and the others simply find fewer rows.
 */
public class OutboxCleanupJob implements SchedulingConfigurer {

    /** The status every service's {@code OutboxEventStatus} enum stores for an event that reached Kafka. */
    static final String PUBLISHED_STATUS = "PUBLISHED";

    private static final String DELETE_BY_DATE_TIME_PUBLISHED = """
            delete from outbox_events
            where event_id in (
                select event_id from outbox_events
                where status = ? and date_time_published is not null and date_time_published < ?
                order by date_time_published
                limit ?)""";

    private static final String DELETE_BY_PUBLISHED_AT = """
            delete from outbox_events
            where event_id in (
                select event_id from outbox_events
                where status = ? and published_at is not null and published_at < ?
                order by published_at
                limit ?)""";

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupJob.class);

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final OutboxCleanupProperties properties;
    private final Clock clock;
    private final OutboxPublishedColumn publishedColumn;

    public OutboxCleanupJob(ObjectProvider<JdbcTemplate> jdbcTemplateProvider, OutboxCleanupProperties properties,
                            Clock clock, OutboxPublishedColumn publishedColumn) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.properties = properties;
        this.clock = clock;
        this.publishedColumn = publishedColumn;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addFixedDelayTask(
                new FixedDelayTask(this::runSafely, properties.interval(), properties.initialDelay()));
    }

    /** One scheduled run; a database error is logged and the next run tries again. */
    void runSafely() {
        try {
            int deleted = deleteExpiredPublishedEvents();
            if (deleted > 0) {
                log.info("Deleted {} expired published outbox events", deleted);
            }
        } catch (DataAccessException ex) {
            log.warn("Outbox cleanup failed; the next run tries again", ex);
        }
    }

    /** Deletes expired PUBLISHED events, batch by batch, and returns how many rows went. */
    public int deleteExpiredPublishedEvents() {
        JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getObject();
        Timestamp cutoff = Timestamp.from(Instant.now(clock).minus(properties.retention()));
        int batchSize = properties.batchSize();
        int total = 0;
        for (int batch = 0; batch < properties.maxBatchesPerRun(); batch++) {
            int deleted = publishedColumn == OutboxPublishedColumn.PUBLISHED_AT
                    ? jdbcTemplate.update(DELETE_BY_PUBLISHED_AT, PUBLISHED_STATUS, cutoff, batchSize)
                    : jdbcTemplate.update(DELETE_BY_DATE_TIME_PUBLISHED, PUBLISHED_STATUS, cutoff, batchSize);
            total += deleted;
            if (deleted < batchSize) {
                break;
            }
        }
        return total;
    }
}
