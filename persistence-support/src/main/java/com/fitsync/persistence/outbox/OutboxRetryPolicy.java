package com.fitsync.persistence.outbox;

import java.time.Duration;
import java.time.Instant;

/**
 * When a relay may try a failed outbox event again. A failure waits 30 seconds, then twice as long after each
 * further failure, up to an hour; after {@link #MAX_ATTEMPTS} attempts the event stays FAILED and is left for
 * someone to look at, so a payload Kafka will never accept does not retry forever.
 *
 * <p>A retried event can reach Kafka after newer events of the same user. Consumers ignore an event they have
 * already processed, but not one that arrives late, so a long Kafka outage can still apply an old update
 * after a newer one. That is the price of not losing the event, which is what happened before retries.
 */
public final class OutboxRetryPolicy {

    /** Attempts after which a failed event is no longer picked up. Migrations that mark old failures as
     * exhausted write this same number. */
    public static final int MAX_ATTEMPTS = 10;

    static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(30);
    static final Duration MAX_RETRY_DELAY = Duration.ofHours(1);

    /** Longest error kept, the size of the {@code error_message} column. */
    public static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private OutboxRetryPolicy() {
    }

    /**
     * The earliest time the next attempt may run, given how many attempts have now failed (1 after the first
     * failure).
     */
    public static Instant nextAttemptAt(int failedAttempts, Instant now) {
        int doublings = Math.max(0, Math.min(failedAttempts - 1, 30));
        Duration delay = FIRST_RETRY_DELAY.multipliedBy(1L << doublings);
        return now.plus(delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay);
    }

    /** Cuts an error message to fit the {@code error_message} column. */
    public static String truncateError(String message) {
        if (message == null || message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
