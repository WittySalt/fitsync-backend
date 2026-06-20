package com.fitsync.analytics.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyWorkoutEntity;
import com.fitsync.analytics.messaging.AnalyticsEventPublisher;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.contracts.events.analytics.WeeklyRecapGeneratedPayload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnalyticsWeeklyRecapServiceTests {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    // Monday 2026-06-15 → prior ISO week is 2026-06-08 .. 2026-06-14.
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-15T00:15:00Z"), ZoneOffset.UTC);

    private AnalyticsUserDailyWorkoutEntity workout(String date, int count, int minutes, int calories) {
        AnalyticsUserDailyWorkoutEntity row = new AnalyticsUserDailyWorkoutEntity();
        row.setUserId(USER_ID);
        row.setActivityDate(LocalDate.parse(date));
        row.setCompletedWorkoutCount(count);
        row.setTotalWorkoutMinutes(minutes);
        row.setTotalCaloriesBurned(calories);
        row.setTotalExerciseCount(0);
        return row;
    }

    private AnalyticsUserDailyNutritionEntity nutrition(String date, int calories) {
        AnalyticsUserDailyNutritionEntity row = new AnalyticsUserDailyNutritionEntity();
        row.setUserId(USER_ID);
        row.setActivityDate(LocalDate.parse(date));
        row.setMealCount(1);
        row.setTotalCaloriesConsumed(calories);
        return row;
    }

    private Slice<UUID> oneUserSlice() {
        return new SliceImpl<>(List.of(USER_ID), PageRequest.of(0, 200), false);
    }

    @Test
    void emitsWeeklyRecapForActiveUserOverPriorIsoWeek() {
        AnalyticsUserDashboardSnapshotRepository snapshotRepository = mock(AnalyticsUserDashboardSnapshotRepository.class);
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
        AnalyticsEventPublisher publisher = mock(AnalyticsEventPublisher.class);

        LocalDate weekStart = LocalDate.parse("2026-06-08");
        LocalDate weekEnd = LocalDate.parse("2026-06-14");
        when(snapshotRepository.findSnapshotUserIds(any(PageRequest.class))).thenReturn(oneUserSlice());
        when(workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(USER_ID, weekStart, weekEnd))
                .thenReturn(List.of(workout("2026-06-09", 2, 60, 500), workout("2026-06-11", 1, 30, 250)));
        when(nutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(USER_ID, weekStart, weekEnd))
                .thenReturn(List.of(nutrition("2026-06-09", 1800), nutrition("2026-06-13", 2000)));

        AnalyticsWeeklyRecapService.RecapSummary summary = new AnalyticsWeeklyRecapService(
                snapshotRepository, workoutRepository, nutritionRepository, publisher, clock).runWeeklyRecapOnce();

        assertThat(summary.emitted()).isEqualTo(1);
        assertThat(summary.skipped()).isZero();
        ArgumentCaptor<WeeklyRecapGeneratedPayload> captor = ArgumentCaptor.forClass(WeeklyRecapGeneratedPayload.class);
        verify(publisher).publishWeeklyRecap(eq(USER_ID), captor.capture());
        WeeklyRecapGeneratedPayload payload = captor.getValue();
        assertThat(payload.weekStart()).isEqualTo(weekStart);
        assertThat(payload.weekEnd()).isEqualTo(weekEnd);
        assertThat(payload.workoutCount()).isEqualTo(3);
        assertThat(payload.workoutMinutes()).isEqualTo(90);
        assertThat(payload.caloriesBurned()).isEqualTo(750);
        assertThat(payload.caloriesConsumed()).isEqualTo(3800);
        assertThat(payload.activeDays()).isEqualTo(3);
    }

    @Test
    void skipsInactiveUserWithoutEmitting() {
        AnalyticsUserDashboardSnapshotRepository snapshotRepository = mock(AnalyticsUserDashboardSnapshotRepository.class);
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
        AnalyticsEventPublisher publisher = mock(AnalyticsEventPublisher.class);

        when(snapshotRepository.findSnapshotUserIds(any(PageRequest.class))).thenReturn(oneUserSlice());
        when(workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(any(), any(), any()))
                .thenReturn(List.of());
        when(nutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(any(), any(), any()))
                .thenReturn(List.of());

        AnalyticsWeeklyRecapService.RecapSummary summary = new AnalyticsWeeklyRecapService(
                snapshotRepository, workoutRepository, nutritionRepository, publisher, clock).runWeeklyRecapOnce();

        assertThat(summary.emitted()).isZero();
        assertThat(summary.skipped()).isEqualTo(1);
        verifyNoInteractions(publisher);
    }
}
