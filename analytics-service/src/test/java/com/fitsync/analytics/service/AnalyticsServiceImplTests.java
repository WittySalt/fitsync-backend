package com.fitsync.analytics.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.analytics.dto.AnalyticsDashboardResponse;
import com.fitsync.analytics.dto.AnalyticsNutritionSummaryResponse;
import com.fitsync.analytics.dto.AnalyticsWorkoutSummaryResponse;
import com.fitsync.analytics.dto.BodyTrendBucket;
import com.fitsync.analytics.entity.AnalyticsUserDailyBodyEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyWorkoutEntity;
import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import com.fitsync.analytics.entity.AnalyticsUserGoalProgressEntity;
import com.fitsync.analytics.repository.AnalyticsMealContributionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import com.fitsync.analytics.repository.AnalyticsWorkoutContributionRepository;
import com.fitsync.analytics.security.CurrentUserPrincipal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsServiceImplTests {

    @Test
    void shouldReturnEmptyDashboardShapeForNewUser() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-06-14T08:00:00Z"), ZoneOffset.UTC);
        AnalyticsUserDashboardSnapshotRepository dashboardRepository = mock(AnalyticsUserDashboardSnapshotRepository.class);
        AnalyticsUserGoalProgressRepository goalRepository = mock(AnalyticsUserGoalProgressRepository.class);
        AnalyticsUserDailyBodyRepository bodyRepository = mock(AnalyticsUserDailyBodyRepository.class);
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(dashboardRepository.findById(userId)).thenReturn(Optional.empty());
        when(goalRepository.findByUserIdOrderByTargetDateAscGoalIdAsc(userId)).thenReturn(List.of());

        AnalyticsServiceImpl service = new AnalyticsServiceImpl(
                dashboardRepository, goalRepository, bodyRepository, workoutRepository, nutritionRepository, fixedClock);

        AnalyticsDashboardResponse response = service.getDashboard(new CurrentUserPrincipal(
                userId,
                "alice@example.com",
                "Alice",
                "USER"));

        assertThat(response.generatedAt()).isEqualTo(Instant.parse("2026-06-14T08:00:00Z"));
        assertThat(response.timezone()).isEqualTo("UTC");
        assertThat(response.workout().weekWorkoutCount()).isZero();
        assertThat(response.goals()).isEmpty();
    }

    @Test
    void shouldMapPersistedSnapshotAndGoalsIntoDashboard() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-06-14T08:00:00Z"), ZoneOffset.UTC);
        AnalyticsUserDashboardSnapshotRepository dashboardRepository = mock(AnalyticsUserDashboardSnapshotRepository.class);
        AnalyticsUserGoalProgressRepository goalRepository = mock(AnalyticsUserGoalProgressRepository.class);
        AnalyticsUserDailyBodyRepository bodyRepository = mock(AnalyticsUserDailyBodyRepository.class);
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID goalId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setUserId(userId);
        snapshot.setTimezone("Asia/Singapore");
        snapshot.setLatestWeightKg(new BigDecimal("78.40"));
        snapshot.setLatestBodyFatRate(new BigDecimal("19.80"));
        snapshot.setBmi(new BigDecimal("24.70"));
        snapshot.setWeightChange30d(new BigDecimal("-1.60"));
        snapshot.setTodayWorkoutCount(1);
        snapshot.setWeekWorkoutCount(4);
        snapshot.setWeekWorkoutMinutes(205);
        snapshot.setActiveStreakDays(3);
        snapshot.setTodayCaloriesConsumed(1860);
        snapshot.setTodayCaloriesTarget(2100);
        snapshot.setTodayProteinGrams(new BigDecimal("132.50"));
        snapshot.setTodayProteinTargetGrams(new BigDecimal("150.00"));
        snapshot.setGeneratedAt(Instant.parse("2026-06-14T07:00:00Z"));

        AnalyticsUserGoalProgressEntity goal = new AnalyticsUserGoalProgressEntity();
        goal.setGoalId(goalId);
        goal.setUserId(userId);
        goal.setGoalType("WEIGHT");
        goal.setGoalStatus("ACTIVE");
        goal.setTargetDate(LocalDate.parse("2026-08-01"));
        goal.setProgressRatio(new BigDecimal("0.4200"));
        goal.setOnTrack(true);
        goal.setLastEvaluatedAt(Instant.parse("2026-06-14T07:00:00Z"));

        when(dashboardRepository.findById(userId)).thenReturn(Optional.of(snapshot));
        when(goalRepository.findByUserIdOrderByTargetDateAscGoalIdAsc(userId)).thenReturn(List.of(goal));

        AnalyticsServiceImpl service = new AnalyticsServiceImpl(
                dashboardRepository, goalRepository, bodyRepository, workoutRepository, nutritionRepository, fixedClock);

        AnalyticsDashboardResponse response = service.getDashboard(new CurrentUserPrincipal(
                userId,
                "alice@example.com",
                "Alice",
                "USER"));

        assertThat(response.generatedAt()).isEqualTo(Instant.parse("2026-06-14T07:00:00Z"));
        assertThat(response.timezone()).isEqualTo("Asia/Singapore");
        assertThat(response.body().latestWeightKg()).isEqualTo(78.4);
        assertThat(response.workout().weekWorkoutCount()).isEqualTo(4);
        assertThat(response.nutrition().todayCaloriesConsumed()).isEqualTo(1860);
        assertThat(response.goals()).singleElement()
                .extracting(AnalyticsDashboardResponse.GoalProgressSummary::goalId,
                        AnalyticsDashboardResponse.GoalProgressSummary::goalType,
                        AnalyticsDashboardResponse.GoalProgressSummary::progressRatio,
                        AnalyticsDashboardResponse.GoalProgressSummary::onTrack)
                .containsExactly(goalId, "WEIGHT", 0.42, true);
    }

    @Test
    void shouldAggregateWorkoutAndNutritionSummariesFromDailyProjections() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-06-14T08:00:00Z"), ZoneOffset.UTC);
        AnalyticsUserDashboardSnapshotRepository dashboardRepository = mock(AnalyticsUserDashboardSnapshotRepository.class);
        AnalyticsUserGoalProgressRepository goalRepository = mock(AnalyticsUserGoalProgressRepository.class);
        AnalyticsUserDailyBodyRepository bodyRepository = mock(AnalyticsUserDailyBodyRepository.class);
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setUserId(userId);
        snapshot.setActiveStreakDays(3);
        snapshot.setTodayWorkoutCount(1);
        snapshot.setWeekWorkoutCount(4);
        snapshot.setWeekWorkoutMinutes(205);
        snapshot.setGeneratedAt(Instant.parse("2026-06-14T08:00:00Z"));
        when(dashboardRepository.findById(userId)).thenReturn(Optional.of(snapshot));

        AnalyticsUserDailyWorkoutEntity workoutDay1 = new AnalyticsUserDailyWorkoutEntity();
        workoutDay1.setUserId(userId);
        workoutDay1.setActivityDate(LocalDate.parse("2026-06-13"));
        workoutDay1.setCompletedWorkoutCount(2);
        workoutDay1.setTotalWorkoutMinutes(90);
        workoutDay1.setTotalCaloriesBurned(600);
        workoutDay1.setTotalExerciseCount(10);
        AnalyticsUserDailyWorkoutEntity workoutDay2 = new AnalyticsUserDailyWorkoutEntity();
        workoutDay2.setUserId(userId);
        workoutDay2.setActivityDate(LocalDate.parse("2026-06-14"));
        workoutDay2.setCompletedWorkoutCount(1);
        workoutDay2.setTotalWorkoutMinutes(45);
        workoutDay2.setTotalCaloriesBurned(350);
        workoutDay2.setTotalExerciseCount(6);
        when(workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                userId, LocalDate.parse("2026-06-13"), LocalDate.parse("2026-06-14")))
                .thenReturn(List.of(workoutDay1, workoutDay2));

        AnalyticsUserDailyNutritionEntity nutritionDay1 = new AnalyticsUserDailyNutritionEntity();
        nutritionDay1.setUserId(userId);
        nutritionDay1.setActivityDate(LocalDate.parse("2026-06-13"));
        nutritionDay1.setTotalCaloriesConsumed(1800);
        nutritionDay1.setTotalProteinGrams(new BigDecimal("130.00"));
        nutritionDay1.setTotalCarbsGrams(new BigDecimal("180.00"));
        nutritionDay1.setTotalFatGrams(new BigDecimal("60.00"));
        nutritionDay1.setCalorieTarget(2000);
        nutritionDay1.setProteinTargetGrams(new BigDecimal("150.00"));
        nutritionDay1.setCalorieAdherenceRatio(new BigDecimal("0.9000"));
        nutritionDay1.setMacroAdherenceScore(new BigDecimal("0.8667"));
        AnalyticsUserDailyNutritionEntity nutritionDay2 = new AnalyticsUserDailyNutritionEntity();
        nutritionDay2.setUserId(userId);
        nutritionDay2.setActivityDate(LocalDate.parse("2026-06-14"));
        nutritionDay2.setTotalCaloriesConsumed(2000);
        nutritionDay2.setTotalProteinGrams(new BigDecimal("150.00"));
        nutritionDay2.setTotalCarbsGrams(new BigDecimal("200.00"));
        nutritionDay2.setTotalFatGrams(new BigDecimal("70.00"));
        nutritionDay2.setCalorieTarget(2100);
        nutritionDay2.setProteinTargetGrams(new BigDecimal("160.00"));
        nutritionDay2.setCalorieAdherenceRatio(new BigDecimal("0.9524"));
        nutritionDay2.setMacroAdherenceScore(new BigDecimal("0.9375"));
        when(nutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                userId, LocalDate.parse("2026-06-13"), LocalDate.parse("2026-06-14")))
                .thenReturn(List.of(nutritionDay1, nutritionDay2));

        AnalyticsServiceImpl service = new AnalyticsServiceImpl(
                dashboardRepository, goalRepository, bodyRepository, workoutRepository, nutritionRepository, fixedClock);

        AnalyticsWorkoutSummaryResponse workoutSummary = service.getWorkoutSummary(
                new CurrentUserPrincipal(userId, "alice@example.com", "Alice", "USER"),
                LocalDate.parse("2026-06-13"),
                LocalDate.parse("2026-06-14"));
        AnalyticsNutritionSummaryResponse nutritionSummary = service.getNutritionSummary(
                new CurrentUserPrincipal(userId, "alice@example.com", "Alice", "USER"),
                LocalDate.parse("2026-06-13"),
                LocalDate.parse("2026-06-14"));

        assertThat(workoutSummary.totalWorkoutCount()).isEqualTo(3);
        assertThat(workoutSummary.totalWorkoutMinutes()).isEqualTo(135);
        assertThat(workoutSummary.activeStreakDays()).isEqualTo(3);
        assertThat(nutritionSummary.totalCaloriesConsumed()).isEqualTo(3800);
        assertThat(nutritionSummary.totalCalorieTarget()).isEqualTo(4100);
        assertThat(nutritionSummary.daysWithinCalorieTargetBand()).isEqualTo(2);
    }

    @Test
    void shouldReturnBodyTrendsForSelectedRange() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-06-14T08:00:00Z"), ZoneOffset.UTC);
        AnalyticsUserDashboardSnapshotRepository dashboardRepository = mock(AnalyticsUserDashboardSnapshotRepository.class);
        AnalyticsUserGoalProgressRepository goalRepository = mock(AnalyticsUserGoalProgressRepository.class);
        AnalyticsUserDailyBodyRepository bodyRepository = mock(AnalyticsUserDailyBodyRepository.class);
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        AnalyticsUserDailyBodyEntity day1 = new AnalyticsUserDailyBodyEntity();
        day1.setUserId(userId);
        day1.setMetricDate(LocalDate.parse("2026-06-01"));
        day1.setWeightKg(new BigDecimal("80.00"));
        AnalyticsUserDailyBodyEntity day2 = new AnalyticsUserDailyBodyEntity();
        day2.setUserId(userId);
        day2.setMetricDate(LocalDate.parse("2026-06-08"));
        day2.setWeightKg(new BigDecimal("79.00"));
        when(bodyRepository.findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(
                userId, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-14")))
                .thenReturn(List.of(day1, day2));

        AnalyticsServiceImpl service = new AnalyticsServiceImpl(
                dashboardRepository, goalRepository, bodyRepository, workoutRepository, nutritionRepository, fixedClock);

        var response = service.getBodyTrends(
                new CurrentUserPrincipal(userId, "alice@example.com", "Alice", "USER"),
                LocalDate.parse("2026-06-01"),
                LocalDate.parse("2026-06-14"),
                BodyTrendBucket.DAY);

        assertThat(response.points()).hasSize(2);
        assertThat(response.points().get(1).weightKg()).isEqualTo(79.0);
    }

    @Test
    void rebuildDashboardPreservesStreakGraceDayWhenYesterdayWasActive() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-06-18T00:05:00Z"), ZoneOffset.UTC);
        AnalyticsUserDashboardSnapshotRepository dashboardRepository = mock(AnalyticsUserDashboardSnapshotRepository.class);
        AnalyticsUserGoalProgressRepository goalRepository = mock(AnalyticsUserGoalProgressRepository.class);
        AnalyticsUserDailyBodyRepository bodyRepository = mock(AnalyticsUserDailyBodyRepository.class);
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setUserId(userId);
        snapshot.setTodayWorkoutCount(3);
        snapshot.setActiveStreakDays(99);
        when(dashboardRepository.findById(userId)).thenReturn(Optional.of(snapshot));
        when(bodyRepository.findTopByUserIdOrderByMetricDateDesc(userId)).thenReturn(Optional.empty());
        when(nutritionRepository.findByUserIdAndActivityDate(userId, LocalDate.parse("2026-06-18")))
                .thenReturn(Optional.empty());
        when(goalRepository.findByUserIdOrderByTargetDateAscGoalIdAsc(userId)).thenReturn(List.of());
        when(workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateDesc(
                userId, LocalDate.parse("2026-06-12"), LocalDate.parse("2026-06-18")))
                .thenReturn(List.of(
                        workoutDay(userId, "2026-06-17", 1, 30),
                        workoutDay(userId, "2026-06-16", 1, 40)));

        rebuildService(dashboardRepository, bodyRepository, nutritionRepository, workoutRepository, goalRepository,
                fixedClock).rebuildDashboard(userId);

        ArgumentCaptor<AnalyticsUserDashboardSnapshotEntity> captor =
                ArgumentCaptor.forClass(AnalyticsUserDashboardSnapshotEntity.class);
        verify(dashboardRepository).save(captor.capture());
        assertThat(captor.getValue().getTodayWorkoutCount()).isZero();
        assertThat(captor.getValue().getActiveStreakDays()).isEqualTo(2);
    }

    @Test
    void rebuildDashboardClearsStreakWhenLatestActivityIsBeforeYesterday() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-06-18T00:05:00Z"), ZoneOffset.UTC);
        AnalyticsUserDashboardSnapshotRepository dashboardRepository = mock(AnalyticsUserDashboardSnapshotRepository.class);
        AnalyticsUserGoalProgressRepository goalRepository = mock(AnalyticsUserGoalProgressRepository.class);
        AnalyticsUserDailyBodyRepository bodyRepository = mock(AnalyticsUserDailyBodyRepository.class);
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setUserId(userId);
        snapshot.setActiveStreakDays(99);
        when(dashboardRepository.findById(userId)).thenReturn(Optional.of(snapshot));
        when(bodyRepository.findTopByUserIdOrderByMetricDateDesc(userId)).thenReturn(Optional.empty());
        when(nutritionRepository.findByUserIdAndActivityDate(userId, LocalDate.parse("2026-06-18")))
                .thenReturn(Optional.empty());
        when(goalRepository.findByUserIdOrderByTargetDateAscGoalIdAsc(userId)).thenReturn(List.of());
        when(workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateDesc(
                userId, LocalDate.parse("2026-06-12"), LocalDate.parse("2026-06-18")))
                .thenReturn(List.of(workoutDay(userId, "2026-06-16", 1, 30)));

        rebuildService(dashboardRepository, bodyRepository, nutritionRepository, workoutRepository, goalRepository,
                fixedClock).rebuildDashboard(userId);

        ArgumentCaptor<AnalyticsUserDashboardSnapshotEntity> captor =
                ArgumentCaptor.forClass(AnalyticsUserDashboardSnapshotEntity.class);
        verify(dashboardRepository).save(captor.capture());
        assertThat(captor.getValue().getTodayWorkoutCount()).isZero();
        assertThat(captor.getValue().getActiveStreakDays()).isZero();
    }

    private AnalyticsRebuildServiceImpl rebuildService(
            AnalyticsUserDashboardSnapshotRepository dashboardRepository,
            AnalyticsUserDailyBodyRepository bodyRepository,
            AnalyticsUserDailyNutritionRepository nutritionRepository,
            AnalyticsUserDailyWorkoutRepository workoutRepository,
            AnalyticsUserGoalProgressRepository goalRepository,
            Clock clock) {
        return new AnalyticsRebuildServiceImpl(
                dashboardRepository,
                bodyRepository,
                nutritionRepository,
                workoutRepository,
                goalRepository,
                mock(AnalyticsMealContributionRepository.class),
                mock(AnalyticsWorkoutContributionRepository.class),
                mock(AnalyticsCacheInvalidationService.class),
                clock);
    }

    private AnalyticsUserDailyWorkoutEntity workoutDay(UUID userId, String date, int count, int minutes) {
        AnalyticsUserDailyWorkoutEntity workout = new AnalyticsUserDailyWorkoutEntity();
        workout.setUserId(userId);
        workout.setActivityDate(LocalDate.parse(date));
        workout.setCompletedWorkoutCount(count);
        workout.setTotalWorkoutMinutes(minutes);
        workout.setTotalCaloriesBurned(0);
        workout.setTotalExerciseCount(0);
        return workout;
    }
}
