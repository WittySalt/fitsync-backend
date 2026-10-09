package com.fitsync.persistence.outbox;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Rows go in batches of {@code batchSize}, each its own short statement and commit. The statement is a fixed
 * string chosen by {@link OutboxPublishedColumn} and every value is a bind parameter. Several instances may run
 * it at once: each statement deletes whatever matches at that moment, so a row is deleted once and the others
 * simply find fewer rows.
 */
public class OutboxCleanupJob implements SchedulingConfigurer {

    /** The status every service's {@code OutboxEventStatus} enum stores for an event that reached Kafka. */
    static final String PUBLISHED_STATUS = "PUBLISHED";

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final OutboxCleanupProperties properties;
    private final Clock clock;
    private final String serviceName;
    private final OutboxPublishedColumn publishedColumn;

    public OutboxCleanupJob(JdbcTemplate jdbcTemplate, OutboxCleanupProperties properties, Clock clock,
                            String serviceName, OutboxPublishedColumn publishedColumn) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.clock = clock;
        this.serviceName = serviceName;
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
                log.info("Deleted {} published {} outbox events older than {}", deleted, serviceName,
                        properties.retention());
            }
        } catch (DataAccessException ex) {
            log.warn("{} outbox cleanup failed: {}", serviceName, ex.getClass().getSimpleName());
        }
    }

    /** Deletes expired PUBLISHED events, batch by batch, and returns how many rows went. */
    public int deleteExpiredPublishedEvents() {
        Timestamp cutoff = Timestamp.from(Instant.now(clock).minus(properties.retention()));
        int total = 0;
        for (int batch = 0; batch < properties.maxBatchesPerRun(); batch++) {
            int deleted = jdbcTemplate.update(
                    publishedColumn.deleteBatchSql(), PUBLISHED_STATUS, cutoff, properties.batchSize());
            total += deleted;
            if (deleted < properties.batchSize()) {
                break;
            }
        }
        return total;
    }
}
