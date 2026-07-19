package com.fitsync.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserStreakRollupEntity;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserStreakRollupRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AnalyticsStreakRollupServiceTests {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-07-14T00:05:00Z");

    private final AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository =
            mock(AnalyticsUserDailyWorkoutRepository.class);
    private final AnalyticsUserStreakRollupRepository streakRollupRepository =
            mock(AnalyticsUserStreakRollupRepository.class);
    private final AnalyticsStreakRollupService service = new AnalyticsStreakRollupService(
            dailyWorkoutRepository, streakRollupRepository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void materializesZeroedRollupForUserWithoutActiveDays() {
        when(dailyWorkoutRepository.findActiveActivityDatesByUserId(USER_ID)).thenReturn(List.of());
        when(streakRollupRepository.findById(USER_ID)).thenReturn(Optional.empty());

        service.rollupUser(USER_ID);

        AnalyticsUserStreakRollupEntity saved = savedRollup();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getBestStreakDays()).isZero();
        assertThat(saved.getBestStreakFrom()).isNull();
        assertThat(saved.getBestStreakTo()).isNull();
        assertThat(saved.getLastActiveDate()).isNull();
        assertThat(saved.getComputedAt()).isEqualTo(NOW);
    }

    @Test
    void materializesLongestRunAcrossGaps() {
        when(dailyWorkoutRepository.findActiveActivityDatesByUserId(USER_ID)).thenReturn(List.of(
                LocalDate.parse("2026-03-02"),
                LocalDate.parse("2026-03-03"),
                LocalDate.parse("2026-03-04"),
                // gap on 2026-03-05
                LocalDate.parse("2026-03-06"),
                LocalDate.parse("2026-03-07"),
                LocalDate.parse("2026-07-07")));
        when(streakRollupRepository.findById(USER_ID)).thenReturn(Optional.empty());

        service.rollupUser(USER_ID);

        AnalyticsUserStreakRollupEntity saved = savedRollup();
        assertThat(saved.getBestStreakDays()).isEqualTo(3);
        assertThat(saved.getBestStreakFrom()).isEqualTo(LocalDate.parse("2026-03-02"));
        assertThat(saved.getBestStreakTo()).isEqualTo(LocalDate.parse("2026-03-04"));
        assertThat(saved.getLastActiveDate()).isEqualTo(LocalDate.parse("2026-07-07"));
    }

    @Test
    void keepsMostRecentRunWhenLengthsTie() {
        when(dailyWorkoutRepository.findActiveActivityDatesByUserId(USER_ID)).thenReturn(List.of(
                LocalDate.parse("2026-05-01"),
                LocalDate.parse("2026-05-02"),
                LocalDate.parse("2026-06-10"),
                LocalDate.parse("2026-06-11")));
        when(streakRollupRepository.findById(USER_ID)).thenReturn(Optional.empty());

        service.rollupUser(USER_ID);

        AnalyticsUserStreakRollupEntity saved = savedRollup();
        assertThat(saved.getBestStreakDays()).isEqualTo(2);
        assertThat(saved.getBestStreakFrom()).isEqualTo(LocalDate.parse("2026-06-10"));
        assertThat(saved.getBestStreakTo()).isEqualTo(LocalDate.parse("2026-06-11"));
    }

    @Test
    void recomputeShrinksBestStreakAfterActiveDaysDisappear() {
        AnalyticsUserStreakRollupEntity existing = new AnalyticsUserStreakRollupEntity();
        existing.setUserId(USER_ID);
        existing.setBestStreakDays(14);
        existing.setBestStreakFrom(LocalDate.parse("2026-03-02"));
        existing.setBestStreakTo(LocalDate.parse("2026-03-15"));
        existing.setLastActiveDate(LocalDate.parse("2026-03-15"));
        existing.setComputedAt(Instant.parse("2026-07-13T00:05:00Z"));
        when(dailyWorkoutRepository.findActiveActivityDatesByUserId(USER_ID))
                .thenReturn(List.of(LocalDate.parse("2026-03-02")));
        when(streakRollupRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

        service.rollupUser(USER_ID);

        AnalyticsUserStreakRollupEntity saved = savedRollup();
        assertThat(saved.getBestStreakDays()).isEqualTo(1);
        assertThat(saved.getBestStreakFrom()).isEqualTo(LocalDate.parse("2026-03-02"));
        assertThat(saved.getBestStreakTo()).isEqualTo(LocalDate.parse("2026-03-02"));
        assertThat(saved.getLastActiveDate()).isEqualTo(LocalDate.parse("2026-03-02"));
        assertThat(saved.getComputedAt()).isEqualTo(NOW);
    }

    private AnalyticsUserStreakRollupEntity savedRollup() {
        ArgumentCaptor<AnalyticsUserStreakRollupEntity> captor =
                ArgumentCaptor.forClass(AnalyticsUserStreakRollupEntity.class);
        verify(streakRollupRepository).save(captor.capture());
        return captor.getValue();
    }
}
