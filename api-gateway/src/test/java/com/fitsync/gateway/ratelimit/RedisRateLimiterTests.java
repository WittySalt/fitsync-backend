package com.fitsync.gateway.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisRateLimiterTests {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String KEY = "fitsync:ratelimit:general:user:42";

    private StringRedisTemplate redisTemplate;
    private MutableClock clock;
    private RedisRateLimiter limiter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(redisTemplate);
        clock = new MutableClock(Instant.parse("2026-10-09T10:00:00Z"));
        limiter = new RedisRateLimiter(provider, Duration.ofSeconds(30), clock);
    }

    @Test
    void allowsRequestsUpToTheLimitAndReportsWhatIsLeft() {
        givenReply(1L, 60_000L);

        RateLimitDecision decision = limiter.tryAcquire(KEY, 3, WINDOW);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.counted()).isTrue();
        assertThat(decision.remaining()).isEqualTo(2);
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(KEY)), eq("60000"));
    }

    @Test
    void theRequestThatReachesTheLimitIsStillAllowed() {
        givenReply(3L, 10_000L);

        RateLimitDecision decision = limiter.tryAcquire(KEY, 3, WINDOW);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remaining()).isZero();
    }

    @Test
    void deniesPastTheLimitWithTheSecondsLeftInTheWindowRoundedUp() {
        givenReply(4L, 12_300L);

        RateLimitDecision decision = limiter.tryAcquire(KEY, 3, WINDOW);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(13);
    }

    @Test
    void retryAfterIsNeverZero() {
        givenReply(4L, 0L);

        assertThat(limiter.tryAcquire(KEY, 3, WINDOW).retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void aNewWindowStartsCountingAgain() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(List.of(4L, 500L))
                .thenReturn(List.of(1L, 60_000L));

        assertThat(limiter.tryAcquire(KEY, 3, WINDOW).allowed()).isFalse();
        assertThat(limiter.tryAcquire(KEY, 3, WINDOW).allowed()).isTrue();
    }

    @Test
    void failsOpenWhenRedisIsDownAndLeavesItAloneDuringTheBackoff() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new RedisConnectionFailureException("down"));

        RateLimitDecision first = limiter.tryAcquire(KEY, 3, WINDOW);
        clock.advance(Duration.ofSeconds(10));
        RateLimitDecision during = limiter.tryAcquire(KEY, 3, WINDOW);

        assertThat(first.allowed()).isTrue();
        assertThat(first.counted()).isFalse();
        assertThat(during.allowed()).isTrue();
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), anyList(), any());

        clock.advance(Duration.ofSeconds(25));
        limiter.tryAcquire(KEY, 3, WINDOW);
        verify(redisTemplate, times(2)).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    void failsOpenOnAReplyItCannotRead() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(List.of("x"));

        RateLimitDecision decision = limiter.tryAcquire(KEY, 3, WINDOW);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.counted()).isFalse();
    }

    private void givenReply(long count, long remainingMillis) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(List.of(count, remainingMillis));
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
