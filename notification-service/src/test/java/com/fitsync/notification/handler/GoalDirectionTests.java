package com.fitsync.notification.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class GoalDirectionTests {

    @Test
    void losingGoalsAreReachedAtOrBelowTheTarget() {
        assertThat(reached("82", "78", "78")).isTrue();
        assertThat(reached("82", "78", "77.9")).isTrue();
        assertThat(reached("82", "78", "78.1")).isFalse();
    }

    @Test
    void gainingGoalsAreReachedAtOrAboveTheTarget() {
        assertThat(reached("60", "64", "64")).isTrue();
        assertThat(reached("60", "64", "63.9")).isFalse();
    }

    @Test
    void withoutADirectionNothingIsReached() {
        assertThat(BodyMetricsRecordedHandler.reached(null, new BigDecimal("78"), new BigDecimal("70"))).isFalse();
        assertThat(BodyMetricsRecordedHandler.reached(new BigDecimal("78"), null, new BigDecimal("70"))).isFalse();
        assertThat(reached("78", "78", "70")).isFalse();
        assertThat(reached("78", "78", "78")).isFalse();
    }

    private static boolean reached(String start, String target, String weight) {
        return BodyMetricsRecordedHandler.reached(new BigDecimal(start), new BigDecimal(target), new BigDecimal(weight));
    }
}
