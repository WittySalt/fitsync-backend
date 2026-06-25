package com.fitsync.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserGoalProgressEntity;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AnalyticsDailyRollupUserProcessorTests {

    @Test
    void expiresPastActiveGoalBeforeRebuildingDashboard() {
        AnalyticsUserGoalProgressRepository goalRepository = mock(AnalyticsUserGoalProgressRepository.class);
        AnalyticsRebuildService rebuildService = mock(AnalyticsRebuildService.class);
        GoalProgressEvaluator goalProgressEvaluator = mock(GoalProgressEvaluator.class);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant evaluatedAt = Instant.parse("2026-06-18T00:05:00Z");

        AnalyticsUserGoalProgressEntity goal = activeGoal(userId, LocalDate.parse("2026-06-17"));
        goal.setProgressRatio(new BigDecimal("0.5000"));
        goal.setCurrentValue(new BigDecimal("10.00"));
        goal.setDaysRemaining(3);
        goal.setOnTrack(true);

        when(goalRepository.findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(userId, "ACTIVE"))
                .thenReturn(List.of(goal));
        when(goalProgressEvaluator.todayForUser(userId)).thenReturn(LocalDate.parse("2026-06-18"));

        AnalyticsDailyRollupUserProcessor processor =
                new AnalyticsDailyRollupUserProcessor(goalRepository, rebuildService, goalProgressEvaluator);

        processor.rollupUser(userId, LocalDate.parse("2026-06-18"), evaluatedAt);

        ArgumentCaptor<AnalyticsUserGoalProgressEntity> captor =
                ArgumentCaptor.forClass(AnalyticsUserGoalProgressEntity.class);
        verify(goalRepository).save(captor.capture());

        assertThat(captor.getValue().getGoalStatus()).isEqualTo("EXPIRED");
        assertThat(captor.getValue().isOnTrack()).isFalse();
        assertThat(captor.getValue().getDaysRemaining()).isNull();
        assertThat(captor.getValue().getProgressRatio()).isNull();
        assertThat(captor.getValue().getCurrentValue()).isNull();
        assertThat(captor.getValue().getLastEvaluatedAt()).isEqualTo(evaluatedAt);
        verify(goalProgressEvaluator).recompute(userId, GoalProgressEvaluator.ALL_GOAL_TYPES, LocalDate.parse("2026-06-18"));
        verify(rebuildService).rebuildDashboard(userId);
    }

    @Test
    void recomputesFutureActiveGoalDaysRemaining() {
        AnalyticsUserGoalProgressRepository goalRepository = mock(AnalyticsUserGoalProgressRepository.class);
        AnalyticsRebuildService rebuildService = mock(AnalyticsRebuildService.class);
        GoalProgressEvaluator goalProgressEvaluator = mock(GoalProgressEvaluator.class);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant evaluatedAt = Instant.parse("2026-06-18T00:05:00Z");

        AnalyticsUserGoalProgressEntity goal = activeGoal(userId, LocalDate.parse("2026-06-28"));
        when(goalRepository.findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(userId, "ACTIVE"))
                .thenReturn(List.of(goal));
        when(goalProgressEvaluator.todayForUser(userId)).thenReturn(LocalDate.parse("2026-06-18"));

        AnalyticsDailyRollupUserProcessor processor =
                new AnalyticsDailyRollupUserProcessor(goalRepository, rebuildService, goalProgressEvaluator);

        processor.rollupUser(userId, LocalDate.parse("2026-06-18"), evaluatedAt);

        ArgumentCaptor<AnalyticsUserGoalProgressEntity> captor =
                ArgumentCaptor.forClass(AnalyticsUserGoalProgressEntity.class);
        verify(goalRepository).save(captor.capture());
        assertThat(captor.getValue().getGoalStatus()).isEqualTo("ACTIVE");
        assertThat(captor.getValue().getDaysRemaining()).isEqualTo(10);
        assertThat(captor.getValue().getLastEvaluatedAt()).isEqualTo(evaluatedAt);
        verify(goalProgressEvaluator).recompute(userId, GoalProgressEvaluator.ALL_GOAL_TYPES, LocalDate.parse("2026-06-18"));
        verify(rebuildService).rebuildDashboard(userId);
    }

    @Test
    void leavesAlreadyCurrentFutureGoalUnchangedButStillRebuildsDashboard() {
        AnalyticsUserGoalProgressRepository goalRepository = mock(AnalyticsUserGoalProgressRepository.class);
        AnalyticsRebuildService rebuildService = mock(AnalyticsRebuildService.class);
        GoalProgressEvaluator goalProgressEvaluator = mock(GoalProgressEvaluator.class);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        AnalyticsUserGoalProgressEntity goal = activeGoal(userId, LocalDate.parse("2026-06-28"));
        goal.setDaysRemaining(10);
        when(goalRepository.findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(userId, "ACTIVE"))
                .thenReturn(List.of(goal));
        when(goalProgressEvaluator.todayForUser(userId)).thenReturn(LocalDate.parse("2026-06-18"));

        AnalyticsDailyRollupUserProcessor processor =
                new AnalyticsDailyRollupUserProcessor(goalRepository, rebuildService, goalProgressEvaluator);

        processor.rollupUser(userId, LocalDate.parse("2026-06-18"), Instant.parse("2026-06-18T00:05:00Z"));

        verify(goalRepository, never()).save(any());
        verify(goalProgressEvaluator).recompute(userId, GoalProgressEvaluator.ALL_GOAL_TYPES, LocalDate.parse("2026-06-18"));
        verify(rebuildService).rebuildDashboard(userId);
    }

    private AnalyticsUserGoalProgressEntity activeGoal(UUID userId, LocalDate targetDate) {
        AnalyticsUserGoalProgressEntity goal = new AnalyticsUserGoalProgressEntity();
        goal.setGoalId(UUID.randomUUID());
        goal.setUserId(userId);
        goal.setGoalType("CALORIES");
        goal.setGoalStatus("ACTIVE");
        goal.setTargetDate(targetDate);
        goal.setTargetValue(new BigDecimal("2100"));
        goal.setLastEvaluatedAt(Instant.parse("2026-06-17T00:00:00Z"));
        return goal;
    }
}
