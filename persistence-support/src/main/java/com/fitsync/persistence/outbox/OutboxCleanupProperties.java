package com.fitsync.persistence.outbox;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * How long a service keeps outbox events after they reach Kafka, and how it removes them.
 *
 * <p>An outbox row holds the full event payload, which can carry personal data such as a weight or an email
 * address. Once the event is PUBLISHED, Kafka and the consumers own it and the row only serves debugging, so it
 * is kept for {@code retention} and then deleted.
 *
 * @param enabled          false turns the cleanup off
 * @param retention        how long a published event is kept after it was published; at least one hour
 * @param batchSize        rows deleted per statement, so one run never holds a long lock; 1 to 5000
 * @param maxBatchesPerRun upper bound on statements per run, so a large backlog is worked off over several runs
 * @param interval         time between runs; at least one minute
 * @param initialDelay     time from start-up to the first run
 */
@ConfigurationProperties(prefix = "fitsync.outbox.cleanup")
public record OutboxCleanupProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("P7D") Duration retention,
        @DefaultValue("500") int batchSize,
        @DefaultValue("100") int maxBatchesPerRun,
        @DefaultValue("PT1H") Duration interval,
        @DefaultValue("PT5M") Duration initialDelay) {

    static final Duration MIN_RETENTION = Duration.ofHours(1);
    static final Duration MIN_INTERVAL = Duration.ofMinutes(1);
    static final int MAX_BATCH_SIZE = 5000;

    public OutboxCleanupProperties {
        if (retention == null || retention.compareTo(MIN_RETENTION) < 0) {
            retention = Duration.ofDays(7);
        }
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            batchSize = 500;
        }
        if (maxBatchesPerRun < 1) {
            maxBatchesPerRun = 100;
        }
        if (interval == null || interval.compareTo(MIN_INTERVAL) < 0) {
            interval = Duration.ofHours(1);
        }
        if (initialDelay == null || initialDelay.isNegative()) {
            initialDelay = Duration.ofMinutes(5);
        }
    }
}
