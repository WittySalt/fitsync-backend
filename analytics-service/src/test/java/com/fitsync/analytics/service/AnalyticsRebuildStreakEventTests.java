package com.fitsync.analytics.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserDailyWorkoutEntity;
import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import com.fitsync.analytics.messaging.AnalyticsEventPublisher;
import com.fitsync.analytics.repository.AnalyticsMealContributionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import com.fitsync.analytics.repository.AnalyticsWorkoutContributionRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsRebuildStreakEventTests {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-15T08:00:00Z"), ZoneOffset.UTC);

    private AnalyticsUserDailyWorkoutEntity workout(LocalDate date) {
        AnalyticsUserDailyWorkoutEntity row = new AnalyticsUserDailyWorkoutEntity();
        row.setUserId(USER_ID);
        row.setActivityDate(date);
        row.setCompletedWorkoutCount(1);
        row.setTotalWorkoutMinutes(30);
        row.setTotalCaloriesBurned(200);
        row.setTotalExerciseCount(4);
        return row;
    }

    private record Fixture(AnalyticsRebuildServiceImpl service, AnalyticsEventPublisher publisher) {
    }

    private Fixture fixtureWithStreak(int previousStreak, List<AnalyticsUserDailyWorkoutEntity> lastSeven) {
        AnalyticsUserDashboardSnapshotRepository snapshotRepository = mock(AnalyticsUserDashboardSnapshotRepository.class);
        AnalyticsUserDailyBodyRepository bodyRepository = mock(AnalyticsUserDailyBodyRepository.class);
        AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserGoalProgressRepository goalRepository = mock(AnalyticsUserGoalProgressRepository.class);
        AnalyticsEventPublisher publisher = mock(AnalyticsEventPublisher.class);

        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setUserId(USER_ID);
        snapshot.setActiveStreakDays(previousStreak);
        when(snapshotRepository.findById(USER_ID)).thenReturn(Optional.of(snapshot));
        when(bodyRepository.findTopByUserIdOrderByMetricDateDesc(USER_ID)).thenReturn(Optional.empty());
        when(nutritionRepository.findByUserIdAndActivityDate(eq(USER_ID), any())).thenReturn(Optional.empty());
        when(workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateDesc(eq(USER_ID), any(), any()))
                .thenReturn(lastSeven);
        when(goalRepository.findByUserIdOrderByTargetDateAscGoalIdAsc(USER_ID)).thenReturn(List.of());

        AnalyticsRebuildServiceImpl service = new AnalyticsRebuildServiceImpl(
                snapshotRepository, bodyRepository, nutritionRepository, workoutRepository, goalRepository,
                mock(AnalyticsMealContributionRepository.class), mock(AnalyticsWorkoutContributionRepository.class),
                mock(com.fitsync.analytics.service.AnalyticsCacheInvalidationService.class), publisher, clock);
        return new Fixture(service, publisher);
    }

    @Test
    void emitsStreakMilestoneWhenStreakCrossesThreshold() {
        LocalDate today = LocalDate.parse("2026-06-15");
        List<AnalyticsUserDailyWorkoutEntity> sevenConsecutive = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            sevenConsecutive.add(workout(today.minusDays(i)));
        }
        Fixture fixture = fixtureWithStreak(6, sevenConsecutive);

        fixture.service().rebuildDashboard(USER_ID);

        verify(fixture.publisher()).publishStreakMilestone(USER_ID, 7, 7, today);
    }

    @Test
    void countsAStreakPastAWeekSoTheThirtyDayMilestoneCanBeReached() {
        LocalDate today = LocalDate.parse("2026-06-15");
        List<AnalyticsUserDailyWorkoutEntity> thirtyConsecutive = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            thirtyConsecutive.add(workout(today.minusDays(i)));
        }
        Fixture fixture = fixtureWithStreak(29, thirtyConsecutive);

        fixture.service().rebuildDashboard(USER_ID);

        verify(fixture.publisher()).publishStreakMilestone(USER_ID, 30, 30, today);
    }

    @Test
    void doesNotEmitWhenNoMilestoneCrossed() {
        LocalDate today = LocalDate.parse("2026-06-15");
        // Only 3 consecutive active days → streak 3, no configured milestone crossed.
        List<AnalyticsUserDailyWorkoutEntity> threeConsecutive = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            threeConsecutive.add(workout(today.minusDays(i)));
        }
        Fixture fixture = fixtureWithStreak(2, threeConsecutive);

        fixture.service().rebuildDashboard(USER_ID);

        verify(fixture.publisher(), never()).publishStreakMilestone(any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), any());
    }
}
