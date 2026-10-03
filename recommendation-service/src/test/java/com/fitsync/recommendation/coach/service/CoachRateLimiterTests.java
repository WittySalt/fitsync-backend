package com.fitsync.recommendation.coach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fitsync.recommendation.coach.config.CoachProperties;
import com.fitsync.recommendation.coach.entity.ArtifactKind;
import com.fitsync.recommendation.coach.entity.CoachTurnRole;
import com.fitsync.recommendation.coach.repository.CoachTurnRepository;
import com.fitsync.recommendation.coach.repository.GeneratedArtifactRepository;
import com.fitsync.recommendation.coach.tool.UserCalendar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CoachRateLimiterTests {

    private static final UUID USER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-03T06:00:00Z");

    private final CoachTurnRepository turns = mock(CoachTurnRepository.class);
    private final GeneratedArtifactRepository artifacts = mock(GeneratedArtifactRepository.class);
    private final UserCalendar calendar = mock(UserCalendar.class);
    private final List<Instant> turnTimes = new ArrayList<>();
    private final List<Instant> draftTimes = new ArrayList<>();
    private CoachRateLimiter limiter;

    @BeforeEach
    void wire() {
        when(turns.findTurnTimesSince(eq(USER), eq(CoachTurnRole.USER), any())).thenAnswer(call -> turnTimes);
        when(artifacts.findCreatedTimesSince(eq(USER), eq(ArtifactKind.SESSION_DRAFT), any()))
                .thenAnswer(call -> draftTimes);
        when(calendar.zone(USER)).thenReturn(ZoneId.of("Asia/Singapore"));
        limiter = limiter(3, 5);
    }

    private CoachRateLimiter limiter(int perHour, int perDay) {
        CoachProperties properties = new CoachProperties(null, null, null, null, null, perHour, perDay);
        return new CoachRateLimiter(turns, artifacts, properties, calendar, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("a user under both limits is let through")
    void underTheLimitPasses() {
        turnTimes.add(NOW.minus(Duration.ofMinutes(10)));
        turnTimes.add(NOW.minus(Duration.ofMinutes(5)));

        assertThatCode(() -> limiter.check(USER, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the hourly limit refuses, and says when the oldest turn of the hour frees a slot, in the user's zone")
    void theHourlyLimitRefuses() {
        turnTimes.add(NOW.minus(Duration.ofMinutes(50)));
        turnTimes.add(NOW.minus(Duration.ofMinutes(20)));
        turnTimes.add(NOW.minus(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> limiter.check(USER, null))
                .isInstanceOfSatisfying(CoachLimitException.class, ex -> {
                    // The 50-minute-old turn leaves the hour in ten minutes, which is 06:10 UTC.
                    assertThat(ex.retryAfterSeconds()).isBetween(600L, 602L);
                    assertThat(ex.getMessage())
                            .isEqualTo("You have asked a lot this hour. You can ask again at 14:10.");
                });
    }

    @Test
    @DisplayName("the zone the client reports is used for the time in the message, and a bad one is ignored")
    void theReportedZoneIsUsed() {
        turnTimes.add(NOW.minus(Duration.ofMinutes(50)));
        turnTimes.add(NOW.minus(Duration.ofMinutes(20)));
        turnTimes.add(NOW.minus(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> limiter.check(USER, "UTC"))
                .hasMessage("You have asked a lot this hour. You can ask again at 06:10.");
        assertThatThrownBy(() -> limiter.check(USER, "Not/AZone"))
                .hasMessage("You have asked a lot this hour. You can ask again at 14:10.");
    }

    @Test
    @DisplayName("the daily limit refuses even when the last hour is quiet, and names the day")
    void theDailyLimitRefuses() {
        for (int hours = 5; hours <= 20; hours += 4) {
            turnTimes.add(NOW.minus(Duration.ofHours(hours)));
        }
        turnTimes.add(NOW.minus(Duration.ofHours(23)));

        assertThatThrownBy(() -> limiter.check(USER, null))
                .isInstanceOfSatisfying(CoachLimitException.class, ex -> {
                    // Five turns in the day and the oldest is 23 hours old, so it frees in an hour.
                    assertThat(ex.retryAfterSeconds()).isBetween(3600L, 3602L);
                    assertThat(ex.getMessage()).startsWith("You have asked a lot this ");
                    assertThat(ex.getMessage()).endsWith("You can ask again at 15:00.");
                });
    }

    @Test
    @DisplayName("session drafts count toward the same limit as chat turns")
    void draftsCountToo() {
        turnTimes.add(NOW.minus(Duration.ofMinutes(30)));
        draftTimes.add(NOW.minus(Duration.ofMinutes(20)));
        draftTimes.add(NOW.minus(Duration.ofMinutes(10)));

        assertThatThrownBy(() -> limiter.check(USER, null)).isInstanceOf(CoachLimitException.class);
    }

    @Test
    @DisplayName("the defaults are 30 an hour and 150 a day")
    void theDefaults() {
        CoachProperties defaults = new CoachProperties(null, null, null, null, null);

        assertThat(defaults.turnsPerHour()).isEqualTo(30);
        assertThat(defaults.turnsPerDay()).isEqualTo(150);
    }
}
