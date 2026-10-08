package com.fitsync.persistence.outbox;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRetryPolicyTests {

    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    @Test
    void firstFailureWaitsThirtySecondsAndEachFurtherFailureDoublesIt() {
        assertThat(OutboxRetryPolicy.nextAttemptAt(1, NOW)).isEqualTo(NOW.plusSeconds(30));
        assertThat(OutboxRetryPolicy.nextAttemptAt(2, NOW)).isEqualTo(NOW.plusSeconds(60));
        assertThat(OutboxRetryPolicy.nextAttemptAt(3, NOW)).isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    void delayStopsGrowingAtOneHour() {
        assertThat(OutboxRetryPolicy.nextAttemptAt(8, NOW)).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(OutboxRetryPolicy.nextAttemptAt(OutboxRetryPolicy.MAX_ATTEMPTS, NOW))
                .isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(OutboxRetryPolicy.nextAttemptAt(1000, NOW)).isEqualTo(NOW.plus(Duration.ofHours(1)));
    }

    @Test
    void zeroOrNegativeFailuresAreTreatedAsTheFirst() {
        assertThat(OutboxRetryPolicy.nextAttemptAt(0, NOW)).isEqualTo(NOW.plusSeconds(30));
        assertThat(OutboxRetryPolicy.nextAttemptAt(-3, NOW)).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void errorsAreCutToTheColumnLength() {
        assertThat(OutboxRetryPolicy.truncateError(null)).isNull();
        assertThat(OutboxRetryPolicy.truncateError("kafka down")).isEqualTo("kafka down");
        assertThat(OutboxRetryPolicy.truncateError("x".repeat(600)))
                .hasSize(OutboxRetryPolicy.MAX_ERROR_MESSAGE_LENGTH);
    }
}
