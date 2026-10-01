package com.fitsync.analytics.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.analytics.config.AnalyticsProperties;
import com.fitsync.analytics.dto.AnalyticsAchievementsResponse;
import com.fitsync.analytics.dto.AnalyticsActivityCalendarResponse;
import com.fitsync.analytics.dto.AnalyticsBodyTrendsResponse;
import com.fitsync.analytics.dto.AnalyticsCompareResponse;
import com.fitsync.analytics.dto.AnalyticsDashboardResponse;
import com.fitsync.analytics.dto.AnalyticsNutritionSeriesResponse;
import com.fitsync.analytics.dto.AnalyticsNutritionSummaryResponse;
import com.fitsync.analytics.dto.AnalyticsStreaksResponse;
import com.fitsync.analytics.dto.AnalyticsWeeklyReportResponse;
import com.fitsync.analytics.dto.AnalyticsWorkoutSeriesResponse;
import com.fitsync.analytics.dto.AnalyticsWorkoutSummaryResponse;
import com.fitsync.analytics.dto.BodyTrendBucket;
import com.fitsync.analytics.dto.ComparePeriod;
import com.fitsync.analytics.entity.AnalyticsUserDailyBodyEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyWorkoutEntity;
import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import com.fitsync.analytics.entity.AnalyticsUserGoalProgressEntity;
import com.fitsync.analytics.entity.AnalyticsUserStreakRollupEntity;
import com.fitsync.analytics.entity.AnalyticsUserWorkoutRecordsEntity;
import com.fitsync.analytics.repository.AnalyticsMealContributionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import com.fitsync.analytics.repository.AnalyticsUserStreakRollupRepository;
import com.fitsync.analytics.repository.AnalyticsUserWorkoutRecordsRepository;
import com.fitsync.analytics.repository.AnalyticsWorkoutContributionRepository;
import com.fitsync.analytics.security.CurrentUserPrincipal;
import com.fitsync.web.error.BadRequestException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        when(goalRepository.findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(userId, "ACTIVE"))
                .thenReturn(List.of());

        AnalyticsServiceImpl service = new AnalyticsServiceImpl(
                dashboardRepository, goalRepository, bodyRepository, workoutRepository, nutritionRepository,
                mock(AnalyticsUserStreakRollupRepository.class), mock(AnalyticsUserWorkoutRecordsRepository.class),
                fixedClock, analyticsProperties());

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
        snapshot.setLatestWaistCm(new BigDecimal("85.50"));
        snapshot.setWaistMeasuredOn(LocalDate.parse("2026-06-12"));
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
        when(goalRepository.findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(userId, "ACTIVE"))
                .thenReturn(List.of(goal));

        AnalyticsServiceImpl service = new AnalyticsServiceImpl(
                dashboardRepository, goalRepository, bodyRepository, workoutRepository, nutritionRepository,
                mock(AnalyticsUserStreakRollupRepository.class), mock(AnalyticsUserWorkoutRecordsRepository.class),
                fixedClock, analyticsProperties());

        AnalyticsDashboardResponse response = service.getDashboard(new CurrentUserPrincipal(
                userId,
                "alice@example.com",
                "Alice",
                "USER"));

        assertThat(response.generatedAt()).isEqualTo(Instant.parse("2026-06-14T07:00:00Z"));
        assertThat(response.timezone()).isEqualTo("Asia/Singapore");
        assertThat(response.body().latestWeightKg()).isEqualTo(78.4);
        assertThat(response.body().latestWaistCm()).isEqualTo(85.5);
        assertThat(response.body().waistMeasuredAt()).isEqualTo(LocalDate.parse("2026-06-12"));
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
    void shouldKeepLatestEvaluatedActiveGoalPerTypeInDashboard() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-06-14T08:00:00Z"), ZoneOffset.UTC);
        AnalyticsUserDashboardSnapshotRepository dashboardRepository = mock(AnalyticsUserDashboardSnapshotRepository.class);
        AnalyticsUserGoalProgressRepository goalRepository = mock(AnalyticsUserGoalProgressRepository.class);
        AnalyticsUserDailyBodyRepository bodyRepository = mock(AnalyticsUserDailyBodyRepository.class);
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID olderGoalId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID newerGoalId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setUserId(userId);
        snapshot.setGeneratedAt(Instant.parse("2026-06-14T08:00:00Z"));

        AnalyticsUserGoalProgressEntity olderGoal = new AnalyticsUserGoalProgressEntity();
        olderGoal.setGoalId(olderGoalId);
        olderGoal.setUserId(userId);
        olderGoal.setGoalType("WEIGHT");
        olderGoal.setGoalStatus("ACTIVE");
        olderGoal.setTargetDate(LocalDate.parse("2026-08-01"));
        olderGoal.setProgressRatio(new BigDecimal("0.2500"));
        olderGoal.setOnTrack(false);
        olderGoal.setLastEvaluatedAt(Instant.parse("2026-06-14T07:00:00Z"));

        AnalyticsUserGoalProgressEntity newerGoal = new AnalyticsUserGoalProgressEntity();
        newerGoal.setGoalId(newerGoalId);
        newerGoal.setUserId(userId);
        newerGoal.setGoalType("WEIGHT");
        newerGoal.setGoalStatus("ACTIVE");
        newerGoal.setTargetDate(LocalDate.parse("2026-08-15"));
        newerGoal.setProgressRatio(new BigDecimal("0.7500"));
        newerGoal.setOnTrack(true);
        newerGoal.setLastEvaluatedAt(Instant.parse("2026-06-14T08:00:00Z"));

        when(dashboardRepository.findById(userId)).thenReturn(Optional.of(snapshot));
        when(goalRepository.findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(userId, "ACTIVE"))
                .thenReturn(List.of(olderGoal, newerGoal));

        AnalyticsServiceImpl service = new AnalyticsServiceImpl(
                dashboardRepository, goalRepository, bodyRepository, workoutRepository, nutritionRepository,
                mock(AnalyticsUserStreakRollupRepository.class), mock(AnalyticsUserWorkoutRecordsRepository.class),
                fixedClock, analyticsProperties());

        AnalyticsDashboardResponse response = service.getDashboard(new CurrentUserPrincipal(
                userId,
                "alice@example.com",
                "Alice",
                "USER"));

        assertThat(response.goals()).singleElement()
                .extracting(AnalyticsDashboardResponse.GoalProgressSummary::goalId,
                        AnalyticsDashboardResponse.GoalProgressSummary::goalType,
                        AnalyticsDashboardResponse.GoalProgressSummary::progressRatio,
                        AnalyticsDashboardResponse.GoalProgressSummary::onTrack)
                .containsExactly(newerGoalId, "WEIGHT", 0.75, true);
    }

    @Test
    void shouldBreakGoalDedupeTieByGoalIdAndExposeTargetFields() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-06-14T08:00:00Z"), ZoneOffset.UTC);
        AnalyticsUserDashboardSnapshotRepository dashboardRepository = mock(AnalyticsUserDashboardSnapshotRepository.class);
        AnalyticsUserGoalProgressRepository goalRepository = mock(AnalyticsUserGoalProgressRepository.class);
        AnalyticsUserDailyBodyRepository bodyRepository = mock(AnalyticsUserDailyBodyRepository.class);
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID lowerGoalId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID higherGoalId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        Instant sameEvaluatedAt = Instant.parse("2026-06-14T07:00:00Z");

        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setUserId(userId);
        snapshot.setGeneratedAt(Instant.parse("2026-06-14T08:00:00Z"));

        AnalyticsUserGoalProgressEntity lower = activeGoal(userId, lowerGoalId, "WEIGHT", sameEvaluatedAt);
        AnalyticsUserGoalProgressEntity higher = activeGoal(userId, higherGoalId, "WEIGHT", sameEvaluatedAt);
        higher.setTargetValue(new BigDecimal("75.00"));
        higher.setTargetUnit("kg");
        higher.setCurrentValue(new BigDecimal("77.00"));

        when(dashboardRepository.findById(userId)).thenReturn(Optional.of(snapshot));
        when(goalRepository.findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(userId, "ACTIVE"))
                .thenReturn(List.of(lower, higher));

        AnalyticsServiceImpl service = new AnalyticsServiceImpl(
                dashboardRepository, goalRepository, bodyRepository, workoutRepository, nutritionRepository,
                mock(AnalyticsUserStreakRollupRepository.class), mock(AnalyticsUserWorkoutRecordsRepository.class),
                fixedClock, analyticsProperties());

        AnalyticsDashboardResponse response = service.getDashboard(new CurrentUserPrincipal(
                userId, "alice@example.com", "Alice", "USER"));

        assertThat(response.goals()).singleElement()
                .extracting(AnalyticsDashboardResponse.GoalProgressSummary::goalId,
                        AnalyticsDashboardResponse.GoalProgressSummary::targetValue,
                        AnalyticsDashboardResponse.GoalProgressSummary::targetUnit,
                        AnalyticsDashboardResponse.GoalProgressSummary::currentValue)
                .containsExactly(higherGoalId, 75.0, "kg", 77.0);
    }

    @Test
    void shouldPreferActiveGoalWithEvaluationTimestampOverNull() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-06-14T08:00:00Z"), ZoneOffset.UTC);
        AnalyticsUserDashboardSnapshotRepository dashboardRepository = mock(AnalyticsUserDashboardSnapshotRepository.class);
        AnalyticsUserGoalProgressRepository goalRepository = mock(AnalyticsUserGoalProgressRepository.class);
        AnalyticsUserDailyBodyRepository bodyRepository = mock(AnalyticsUserDailyBodyRepository.class);
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID evaluatedGoalId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID neverEvaluatedGoalId = UUID.fromString("66666666-6666-6666-6666-666666666666");

        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setUserId(userId);
        snapshot.setGeneratedAt(Instant.parse("2026-06-14T08:00:00Z"));

        AnalyticsUserGoalProgressEntity evaluated = activeGoal(userId, evaluatedGoalId, "PROTEIN",
                Instant.parse("2026-06-14T07:00:00Z"));
        AnalyticsUserGoalProgressEntity neverEvaluated = activeGoal(userId, neverEvaluatedGoalId, "PROTEIN", null);

        when(dashboardRepository.findById(userId)).thenReturn(Optional.of(snapshot));
        when(goalRepository.findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(userId, "ACTIVE"))
                .thenReturn(List.of(evaluated, neverEvaluated));

        AnalyticsServiceImpl service = new AnalyticsServiceImpl(
                dashboardRepository, goalRepository, bodyRepository, workoutRepository, nutritionRepository,
                mock(AnalyticsUserStreakRollupRepository.class), mock(AnalyticsUserWorkoutRecordsRepository.class),
                fixedClock, analyticsProperties());

        AnalyticsDashboardResponse response = service.getDashboard(new CurrentUserPrincipal(
                userId, "alice@example.com", "Alice", "USER"));

        assertThat(response.goals()).singleElement()
                .extracting(AnalyticsDashboardResponse.GoalProgressSummary::goalId)
                .isEqualTo(evaluatedGoalId);
    }

    @Test
    void shouldKeepIncumbentGoalWhenTieAndCandidateGoalIdLower() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-06-14T08:00:00Z"), ZoneOffset.UTC);
        AnalyticsUserDashboardSnapshotRepository dashboardRepository = mock(AnalyticsUserDashboardSnapshotRepository.class);
        AnalyticsUserGoalProgressRepository goalRepository = mock(AnalyticsUserGoalProgressRepository.class);
        AnalyticsUserDailyBodyRepository bodyRepository = mock(AnalyticsUserDailyBodyRepository.class);
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID higherGoalId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID lowerGoalId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        Instant sameEvaluatedAt = Instant.parse("2026-06-14T07:00:00Z");

        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setUserId(userId);
        snapshot.setGeneratedAt(Instant.parse("2026-06-14T08:00:00Z"));

        when(dashboardRepository.findById(userId)).thenReturn(Optional.of(snapshot));
        when(goalRepository.findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(userId, "ACTIVE"))
                .thenReturn(List.of(
                        activeGoal(userId, higherGoalId, "WEIGHT", sameEvaluatedAt),
                        activeGoal(userId, lowerGoalId, "WEIGHT", sameEvaluatedAt)));

        AnalyticsServiceImpl service = new AnalyticsServiceImpl(
                dashboardRepository, goalRepository, bodyRepository, workoutRepository, nutritionRepository,
                mock(AnalyticsUserStreakRollupRepository.class), mock(AnalyticsUserWorkoutRecordsRepository.class),
                fixedClock, analyticsProperties());

        AnalyticsDashboardResponse response = service.getDashboard(new CurrentUserPrincipal(
                userId, "alice@example.com", "Alice", "USER"));

        assertThat(response.goals()).singleElement()
                .extracting(AnalyticsDashboardResponse.GoalProgressSummary::goalId)
                .isEqualTo(higherGoalId);
    }

    @Test
    void getGoalProgressCollapsesToLatestEvaluatedActiveGoalPerType() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-06-14T08:00:00Z"), ZoneOffset.UTC);
        AnalyticsUserDashboardSnapshotRepository dashboardRepository = mock(AnalyticsUserDashboardSnapshotRepository.class);
        AnalyticsUserGoalProgressRepository goalRepository = mock(AnalyticsUserGoalProgressRepository.class);
        AnalyticsUserDailyBodyRepository bodyRepository = mock(AnalyticsUserDailyBodyRepository.class);
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID olderGoalId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID newerGoalId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID proteinGoalId = UUID.fromString("55555555-5555-5555-5555-555555555555");

        AnalyticsUserGoalProgressEntity olderWeight = activeGoal(userId, olderGoalId, "WEIGHT",
                Instant.parse("2026-06-14T06:00:00Z"));
        AnalyticsUserGoalProgressEntity newerWeight = activeGoal(userId, newerGoalId, "WEIGHT",
                Instant.parse("2026-06-14T07:30:00Z"));
        AnalyticsUserGoalProgressEntity protein = activeGoal(userId, proteinGoalId, "PROTEIN",
                Instant.parse("2026-06-14T07:00:00Z"));

        when(goalRepository.findByUserIdOrderByTargetDateAscGoalIdAsc(userId))
                .thenReturn(List.of(olderWeight, newerWeight, protein));

        AnalyticsServiceImpl service = new AnalyticsServiceImpl(
                dashboardRepository, goalRepository, bodyRepository, workoutRepository, nutritionRepository,
                mock(AnalyticsUserStreakRollupRepository.class), mock(AnalyticsUserWorkoutRecordsRepository.class),
                fixedClock, analyticsProperties());

        com.fitsync.analytics.dto.AnalyticsGoalProgressResponse response = service.getGoalProgress(
                new CurrentUserPrincipal(userId, "alice@example.com", "Alice", "USER"), "ACTIVE");

        // Duplicate WEIGHT rows collapse to the latest evaluated; PROTEIN stays.
        assertThat(response.goals())
                .extracting(com.fitsync.analytics.dto.AnalyticsGoalProgressResponse.GoalProgressView::goalId,
                        com.fitsync.analytics.dto.AnalyticsGoalProgressResponse.GoalProgressView::goalType)
                .containsExactlyInAnyOrder(
                        tuple(newerGoalId, "WEIGHT"),
                        tuple(proteinGoalId, "PROTEIN"));
    }

    private AnalyticsUserGoalProgressEntity activeGoal(UUID userId, UUID goalId, String goalType, Instant evaluatedAt) {
        AnalyticsUserGoalProgressEntity goal = new AnalyticsUserGoalProgressEntity();
        goal.setGoalId(goalId);
        goal.setUserId(userId);
        goal.setGoalType(goalType);
        goal.setGoalStatus("ACTIVE");
        goal.setTargetDate(LocalDate.parse("2026-08-01"));
        goal.setProgressRatio(new BigDecimal("0.5000"));
        goal.setOnTrack(true);
        goal.setLastEvaluatedAt(evaluatedAt);
        return goal;
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
                dashboardRepository, goalRepository, bodyRepository, workoutRepository, nutritionRepository,
                mock(AnalyticsUserStreakRollupRepository.class), mock(AnalyticsUserWorkoutRecordsRepository.class),
                fixedClock, analyticsProperties());

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
        day1.setWeightDeltaFromPrevious(null);
        AnalyticsUserDailyBodyEntity day2 = new AnalyticsUserDailyBodyEntity();
        day2.setUserId(userId);
        day2.setMetricDate(LocalDate.parse("2026-06-08"));
        day2.setWeightKg(new BigDecimal("79.00"));
        day2.setWeightDeltaFromPrevious(new BigDecimal("-1.00"));
        when(bodyRepository.findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(
                userId, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-14")))
                .thenReturn(List.of(day1, day2));

        AnalyticsServiceImpl service = new AnalyticsServiceImpl(
                dashboardRepository, goalRepository, bodyRepository, workoutRepository, nutritionRepository,
                mock(AnalyticsUserStreakRollupRepository.class), mock(AnalyticsUserWorkoutRecordsRepository.class),
                fixedClock, analyticsProperties());

        var response = service.getBodyTrends(
                new CurrentUserPrincipal(userId, "alice@example.com", "Alice", "USER"),
                LocalDate.parse("2026-06-01"),
                LocalDate.parse("2026-06-14"),
                BodyTrendBucket.DAY);

        assertThat(response.points()).hasSize(2);
        assertThat(response.points().get(1).date()).isEqualTo(LocalDate.parse("2026-06-08"));
        assertThat(response.points().get(1).weightKg()).isEqualTo(79.0);
        assertThat(response.points().get(1).weightDeltaFromPrevious()).isEqualTo(-1.0);
    }

    @Test
    void bodyTrendsAllowsRangesExactlyAtConfiguredBucketCaps() {
        assertTrendRangePasses(BodyTrendBucket.DAY, 370);
        assertTrendRangePasses(BodyTrendBucket.WEEK, 1095);
        assertTrendRangePasses(BodyTrendBucket.MONTH, 1830);
    }

    @Test
    void bodyTrendsRejectsRangesBeyondConfiguredBucketCapsBeforeLoadingRows() {
        assertTrendRangeRejected(BodyTrendBucket.DAY, 370);
        assertTrendRangeRejected(BodyTrendBucket.WEEK, 1095);
        assertTrendRangeRejected(BodyTrendBucket.MONTH, 1830);
    }

    @Test
    void bodyTrendsAllowsLongerRangesForWeekAndMonthBuckets() {
        assertTrendRangePasses(BodyTrendBucket.WEEK, 800);
        assertTrendRangePasses(BodyTrendBucket.MONTH, 800);
    }

    @Test
    void bodyTrendsKeepsExistingNullAndInvertedRangeValidation() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        AnalyticsUserDailyBodyRepository bodyRepository = mock(AnalyticsUserDailyBodyRepository.class);
        AnalyticsServiceImpl service = bodyTrendService(bodyRepository);
        CurrentUserPrincipal principal = principal(userId);

        assertThatThrownBy(() -> service.getBodyTrends(
                principal,
                null,
                LocalDate.parse("2026-01-01"),
                BodyTrendBucket.DAY))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("from and to are required");
        assertThatThrownBy(() -> service.getBodyTrends(
                principal,
                LocalDate.parse("2026-01-02"),
                LocalDate.parse("2026-01-01"),
                BodyTrendBucket.DAY))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("to must not be before from");
        verify(bodyRepository, never()).findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(any(), any(), any());
    }

    @Test
    void workoutSummaryRejectsRangesBeyondDayCapBeforeLoadingRows() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsServiceImpl service = workoutSummaryService(workoutRepository);
        LocalDate from = LocalDate.parse("2026-01-01");

        assertThatThrownBy(() -> service.getWorkoutSummary(principal(userId), from, from.plusDays(371)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Date range must not exceed 370 days");
        verify(workoutRepository, never()).findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(any(), any(), any());
    }

    @Test
    void workoutSeriesDayReturnsRawDailyPointsAscending() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        LocalDate from = LocalDate.parse("2026-06-01");
        LocalDate to = LocalDate.parse("2026-06-02");
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        when(workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, from, to))
                .thenReturn(List.of(
                        workoutDay(userId, "2026-06-01", 1, 30, 250, 4),
                        workoutDay(userId, "2026-06-02", 2, 80, 700, 9)));

        AnalyticsWorkoutSeriesResponse response = workoutSeriesService(workoutRepository)
                .getWorkoutSeries(principal(userId), from, to, BodyTrendBucket.DAY);

        assertThat(response.points()).hasSize(2);
        assertThat(response.points().get(0))
                .extracting(
                        AnalyticsWorkoutSeriesResponse.WorkoutSeriesPoint::date,
                        AnalyticsWorkoutSeriesResponse.WorkoutSeriesPoint::completedWorkoutCount,
                        AnalyticsWorkoutSeriesResponse.WorkoutSeriesPoint::totalWorkoutMinutes,
                        AnalyticsWorkoutSeriesResponse.WorkoutSeriesPoint::totalCaloriesBurned,
                        AnalyticsWorkoutSeriesResponse.WorkoutSeriesPoint::totalExerciseCount)
                .containsExactly(LocalDate.parse("2026-06-01"), 1, 30, 250, 4);
        assertThat(response.points().get(1).date()).isEqualTo(LocalDate.parse("2026-06-02"));
    }

    @Test
    void workoutSeriesWeekSumsDailyRowsByIsoWeekStartAscending() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        LocalDate from = LocalDate.parse("2026-06-02");
        LocalDate to = LocalDate.parse("2026-06-10");
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        when(workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, from, to))
                .thenReturn(List.of(
                        workoutDay(userId, "2026-06-02", 1, 30, 250, 4),
                        workoutDay(userId, "2026-06-04", 2, 60, 500, 8),
                        workoutDay(userId, "2026-06-08", 3, 90, 800, 12),
                        workoutDay(userId, "2026-06-10", 1, 40, 300, 5)));

        AnalyticsWorkoutSeriesResponse response = workoutSeriesService(workoutRepository)
                .getWorkoutSeries(principal(userId), from, to, BodyTrendBucket.WEEK);

        assertThat(response.points()).hasSize(2);
        assertThat(response.points().get(0))
                .extracting(
                        AnalyticsWorkoutSeriesResponse.WorkoutSeriesPoint::date,
                        AnalyticsWorkoutSeriesResponse.WorkoutSeriesPoint::completedWorkoutCount,
                        AnalyticsWorkoutSeriesResponse.WorkoutSeriesPoint::totalWorkoutMinutes,
                        AnalyticsWorkoutSeriesResponse.WorkoutSeriesPoint::totalCaloriesBurned,
                        AnalyticsWorkoutSeriesResponse.WorkoutSeriesPoint::totalExerciseCount)
                .containsExactly(LocalDate.parse("2026-06-01"), 3, 90, 750, 12);
        assertThat(response.points().get(1))
                .extracting(
                        AnalyticsWorkoutSeriesResponse.WorkoutSeriesPoint::date,
                        AnalyticsWorkoutSeriesResponse.WorkoutSeriesPoint::completedWorkoutCount,
                        AnalyticsWorkoutSeriesResponse.WorkoutSeriesPoint::totalWorkoutMinutes,
                        AnalyticsWorkoutSeriesResponse.WorkoutSeriesPoint::totalCaloriesBurned,
                        AnalyticsWorkoutSeriesResponse.WorkoutSeriesPoint::totalExerciseCount)
                .containsExactly(LocalDate.parse("2026-06-08"), 4, 130, 1100, 17);
    }

    @Test
    void workoutSeriesEmptyRangeReturnsEmptyPoints() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        LocalDate from = LocalDate.parse("2026-06-01");
        LocalDate to = LocalDate.parse("2026-06-07");
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        when(workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, from, to))
                .thenReturn(List.of());

        AnalyticsWorkoutSeriesResponse response = workoutSeriesService(workoutRepository)
                .getWorkoutSeries(principal(userId), from, to, BodyTrendBucket.WEEK);

        assertThat(response.points()).isEmpty();
    }

    @Test
    void workoutSeriesRejectsRangesBeyondBucketCapBeforeLoadingRows() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        LocalDate from = LocalDate.parse("2026-01-01");
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);

        assertThatThrownBy(() -> workoutSeriesService(workoutRepository)
                .getWorkoutSeries(principal(userId), from, from.plusDays(371), BodyTrendBucket.DAY))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Date range for DAY bucket must not exceed 370 days");
        verify(workoutRepository, never()).findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(any(), any(), any());
    }

    @Test
    void activityCalendarReturnsDensePointsWithZeroForInactiveDays() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        LocalDate from = LocalDate.parse("2026-06-01");
        LocalDate to = LocalDate.parse("2026-06-05");
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        when(workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, from, to))
                .thenReturn(List.of(
                        workoutDay(userId, "2026-06-01", 1, 30),
                        workoutDay(userId, "2026-06-03", 2, 60)));

        AnalyticsActivityCalendarResponse response = workoutSeriesService(workoutRepository)
                .getActivityCalendar(principal(userId), from, to);

        assertThat(response.days())
                .extracting(
                        AnalyticsActivityCalendarResponse.ActivityCalendarDay::date,
                        AnalyticsActivityCalendarResponse.ActivityCalendarDay::workoutCount,
                        AnalyticsActivityCalendarResponse.ActivityCalendarDay::workoutMinutes)
                .containsExactly(
                        tuple(LocalDate.parse("2026-06-01"), 1, 30),
                        tuple(LocalDate.parse("2026-06-02"), 0, 0),
                        tuple(LocalDate.parse("2026-06-03"), 2, 60),
                        tuple(LocalDate.parse("2026-06-04"), 0, 0),
                        tuple(LocalDate.parse("2026-06-05"), 0, 0));
    }

    @Test
    void activityCalendarSingleDayRangeReturnsOnePoint() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        LocalDate day = LocalDate.parse("2026-06-01");
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        when(workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, day, day))
                .thenReturn(List.of(workoutDay(userId, "2026-06-01", 3, 95)));

        AnalyticsActivityCalendarResponse response = workoutSeriesService(workoutRepository)
                .getActivityCalendar(principal(userId), day, day);

        assertThat(response.days())
                .extracting(
                        AnalyticsActivityCalendarResponse.ActivityCalendarDay::date,
                        AnalyticsActivityCalendarResponse.ActivityCalendarDay::workoutCount,
                        AnalyticsActivityCalendarResponse.ActivityCalendarDay::workoutMinutes)
                .containsExactly(tuple(day, 3, 95));
    }

    @Test
    void activityCalendarRejectsRangesBeyondDayCapBeforeLoadingRows() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        LocalDate from = LocalDate.parse("2026-01-01");
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);

        assertThatThrownBy(() -> workoutSeriesService(workoutRepository)
                .getActivityCalendar(principal(userId), from, from.plusDays(371)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Date range must not exceed 370 days");
        verify(workoutRepository, never()).findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(any(), any(), any());
    }

    @Test
    void nutritionSeriesDayReturnsRawDailyPointsAscending() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        LocalDate from = LocalDate.parse("2026-06-01");
        LocalDate to = LocalDate.parse("2026-06-02");
        AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
        when(nutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, from, to))
                .thenReturn(List.of(
                        nutritionDay(userId, "2026-06-01", 1800, "120.50", "200.00", "60.00", 2100, "150.00"),
                        nutritionDay(userId, "2026-06-02", 2000, "140.00", "220.00", "70.00", 2200, "160.00")));

        AnalyticsNutritionSeriesResponse response = nutritionSeriesService(nutritionRepository)
                .getNutritionSeries(principal(userId), from, to, BodyTrendBucket.DAY);

        assertThat(response.points()).hasSize(2);
        assertThat(response.points().get(0))
                .extracting(
                        AnalyticsNutritionSeriesResponse.NutritionSeriesPoint::date,
                        AnalyticsNutritionSeriesResponse.NutritionSeriesPoint::totalCaloriesConsumed,
                        AnalyticsNutritionSeriesResponse.NutritionSeriesPoint::totalProteinGrams,
                        AnalyticsNutritionSeriesResponse.NutritionSeriesPoint::totalCarbsGrams,
                        AnalyticsNutritionSeriesResponse.NutritionSeriesPoint::totalFatGrams,
                        AnalyticsNutritionSeriesResponse.NutritionSeriesPoint::calorieTarget,
                        AnalyticsNutritionSeriesResponse.NutritionSeriesPoint::proteinTargetGrams)
                .containsExactly(LocalDate.parse("2026-06-01"), 1800, 120.5, 200.0, 60.0, 2100, 150.0);
        assertThat(response.points().get(1).date()).isEqualTo(LocalDate.parse("2026-06-02"));
    }

    @Test
    void nutritionSeriesMonthSumsDailyRowsByMonthStartWithSummaryNullParity() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        LocalDate from = LocalDate.parse("2026-01-05");
        LocalDate to = LocalDate.parse("2026-02-20");
        AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
        when(nutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, from, to))
                .thenReturn(List.of(
                        nutritionDay(userId, "2026-01-05", 1800, "120.50", "200.00", "60.00", 2100, "150.00"),
                        nutritionDay(userId, "2026-01-31", 1900, "130.00", "210.25", "65.00", 2200, "160.00"),
                        nutritionDay(userId, "2026-02-01", null, null, null, null, null, null),
                        nutritionDay(userId, "2026-02-20", null, null, null, null, null, null)));

        AnalyticsNutritionSeriesResponse response = nutritionSeriesService(nutritionRepository)
                .getNutritionSeries(principal(userId), from, to, BodyTrendBucket.MONTH);

        assertThat(response.points()).hasSize(2);
        assertThat(response.points().get(0))
                .extracting(
                        AnalyticsNutritionSeriesResponse.NutritionSeriesPoint::date,
                        AnalyticsNutritionSeriesResponse.NutritionSeriesPoint::totalCaloriesConsumed,
                        AnalyticsNutritionSeriesResponse.NutritionSeriesPoint::totalProteinGrams,
                        AnalyticsNutritionSeriesResponse.NutritionSeriesPoint::totalCarbsGrams,
                        AnalyticsNutritionSeriesResponse.NutritionSeriesPoint::totalFatGrams,
                        AnalyticsNutritionSeriesResponse.NutritionSeriesPoint::calorieTarget,
                        AnalyticsNutritionSeriesResponse.NutritionSeriesPoint::proteinTargetGrams)
                .containsExactly(LocalDate.parse("2026-01-01"), 3700, 250.5, 410.25, 125.0, 4300, 310.0);
        assertThat(response.points().get(1))
                .extracting(
                        AnalyticsNutritionSeriesResponse.NutritionSeriesPoint::date,
                        AnalyticsNutritionSeriesResponse.NutritionSeriesPoint::totalCaloriesConsumed,
                        AnalyticsNutritionSeriesResponse.NutritionSeriesPoint::totalProteinGrams,
                        AnalyticsNutritionSeriesResponse.NutritionSeriesPoint::totalCarbsGrams,
                        AnalyticsNutritionSeriesResponse.NutritionSeriesPoint::totalFatGrams,
                        AnalyticsNutritionSeriesResponse.NutritionSeriesPoint::calorieTarget,
                        AnalyticsNutritionSeriesResponse.NutritionSeriesPoint::proteinTargetGrams)
                .containsExactly(LocalDate.parse("2026-02-01"), 0, null, null, null, null, null);
    }

    @Test
    void nutritionSeriesEmptyRangeReturnsEmptyPoints() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        LocalDate from = LocalDate.parse("2026-06-01");
        LocalDate to = LocalDate.parse("2026-06-07");
        AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
        when(nutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, from, to))
                .thenReturn(List.of());

        AnalyticsNutritionSeriesResponse response = nutritionSeriesService(nutritionRepository)
                .getNutritionSeries(principal(userId), from, to, BodyTrendBucket.MONTH);

        assertThat(response.points()).isEmpty();
    }

    @Test
    void nutritionSeriesRejectsRangesBeyondBucketCapBeforeLoadingRows() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        LocalDate from = LocalDate.parse("2026-01-01");
        AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);

        assertThatThrownBy(() -> nutritionSeriesService(nutritionRepository)
                .getNutritionSeries(principal(userId), from, from.plusDays(371), BodyTrendBucket.DAY))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Date range for DAY bucket must not exceed 370 days");
        verify(nutritionRepository, never()).findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(any(), any(), any());
    }

    @Test
    void summaryCompareWeekUsesDayOffsetAlignedIsoWeekWindowsAndComputesDeltas() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
        when(workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                userId, LocalDate.parse("2026-06-15"), LocalDate.parse("2026-06-18")))
                .thenReturn(List.of(
                        workoutDay(userId, "2026-06-15", 2, 60, 500, 8),
                        workoutDay(userId, "2026-06-18", 1, 30, 250, 4)));
        when(workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                userId, LocalDate.parse("2026-06-08"), LocalDate.parse("2026-06-11")))
                .thenReturn(List.of(workoutDay(userId, "2026-06-10", 2, 45, 300, 6)));
        when(nutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                userId, LocalDate.parse("2026-06-15"), LocalDate.parse("2026-06-18")))
                .thenReturn(List.of(nutritionDay(userId, "2026-06-18", 4000, "260.00", "420.00", "130.00", 4300, "300.00")));
        when(nutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                userId, LocalDate.parse("2026-06-08"), LocalDate.parse("2026-06-11")))
                .thenReturn(List.of(nutritionDay(userId, "2026-06-10", 3000, "200.00", "300.00", "100.00", 3600, "240.00")));

        AnalyticsCompareResponse response = compareService(
                workoutRepository,
                nutritionRepository,
                Clock.fixed(Instant.parse("2026-06-18T10:00:00Z"), ZoneOffset.UTC))
                .getSummaryCompare(principal(userId), ComparePeriod.WEEK);

        assertThat(response.currentWindow())
                .extracting(AnalyticsCompareResponse.PeriodWindow::from, AnalyticsCompareResponse.PeriodWindow::to)
                .containsExactly(LocalDate.parse("2026-06-15"), LocalDate.parse("2026-06-18"));
        assertThat(response.previousWindow())
                .extracting(AnalyticsCompareResponse.PeriodWindow::from, AnalyticsCompareResponse.PeriodWindow::to)
                .containsExactly(LocalDate.parse("2026-06-08"), LocalDate.parse("2026-06-11"));
        assertThat(response.workout().completedWorkoutCount())
                .extracting(
                        AnalyticsCompareResponse.MetricComparison::current,
                        AnalyticsCompareResponse.MetricComparison::previous,
                        AnalyticsCompareResponse.MetricComparison::delta,
                        AnalyticsCompareResponse.MetricComparison::deltaPct)
                .containsExactly(3.0, 2.0, 1.0, 50.0);
        assertThat(response.workout().totalWorkoutMinutes().delta()).isEqualTo(45.0);
        assertThat(response.nutrition().totalCaloriesConsumed())
                .extracting(
                        AnalyticsCompareResponse.MetricComparison::current,
                        AnalyticsCompareResponse.MetricComparison::previous,
                        AnalyticsCompareResponse.MetricComparison::delta,
                        AnalyticsCompareResponse.MetricComparison::deltaPct)
                .containsExactly(4000.0, 3000.0, 1000.0, 33.3333);
        assertThat(response.nutrition().totalProteinGrams().delta()).isEqualTo(60.0);
    }

    @Test
    void summaryCompareMonthUsesPreviousMonthClampAtMonthEnd() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
        when(workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                userId, LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-31")))
                .thenReturn(List.of(workoutDay(userId, "2026-03-31", 4, 120, 900, 16)));
        when(workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                userId, LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-28")))
                .thenReturn(List.of(workoutDay(userId, "2026-02-28", 2, 60, 500, 8)));
        when(nutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(any(), any(), any()))
                .thenReturn(List.of());

        AnalyticsCompareResponse response = compareService(
                workoutRepository,
                nutritionRepository,
                Clock.fixed(Instant.parse("2026-03-31T10:00:00Z"), ZoneOffset.UTC))
                .getSummaryCompare(principal(userId), ComparePeriod.MONTH);

        assertThat(response.currentWindow())
                .extracting(AnalyticsCompareResponse.PeriodWindow::from, AnalyticsCompareResponse.PeriodWindow::to)
                .containsExactly(LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-31"));
        assertThat(response.previousWindow())
                .extracting(AnalyticsCompareResponse.PeriodWindow::from, AnalyticsCompareResponse.PeriodWindow::to)
                .containsExactly(LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-28"));
    }

    @Test
    void summaryCompareMonthHandlesCrossYearBoundaryAndPreviousZeroPct() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
        when(workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                userId, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-05")))
                .thenReturn(List.of(workoutDay(userId, "2026-01-05", 1, 30, 250, 4)));
        when(workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                userId, LocalDate.parse("2025-12-01"), LocalDate.parse("2025-12-05")))
                .thenReturn(List.of());
        when(nutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                userId, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-05")))
                .thenReturn(List.of(nutritionDay(userId, "2026-01-05", 1800, null, null, null, null, null)));
        when(nutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                userId, LocalDate.parse("2025-12-01"), LocalDate.parse("2025-12-05")))
                .thenReturn(List.of());

        AnalyticsCompareResponse response = compareService(
                workoutRepository,
                nutritionRepository,
                Clock.fixed(Instant.parse("2026-01-05T10:00:00Z"), ZoneOffset.UTC))
                .getSummaryCompare(principal(userId), ComparePeriod.MONTH);

        assertThat(response.previousWindow())
                .extracting(AnalyticsCompareResponse.PeriodWindow::from, AnalyticsCompareResponse.PeriodWindow::to)
                .containsExactly(LocalDate.parse("2025-12-01"), LocalDate.parse("2025-12-05"));
        assertThat(response.workout().completedWorkoutCount())
                .extracting(
                        AnalyticsCompareResponse.MetricComparison::current,
                        AnalyticsCompareResponse.MetricComparison::previous,
                        AnalyticsCompareResponse.MetricComparison::delta,
                        AnalyticsCompareResponse.MetricComparison::deltaPct)
                .containsExactly(1.0, 0.0, 1.0, null);
        assertThat(response.nutrition().totalCaloriesConsumed())
                .extracting(
                        AnalyticsCompareResponse.MetricComparison::current,
                        AnalyticsCompareResponse.MetricComparison::previous,
                        AnalyticsCompareResponse.MetricComparison::delta,
                        AnalyticsCompareResponse.MetricComparison::deltaPct)
                .containsExactly(1800.0, 0.0, 1800.0, null);
    }

    @Test
    void summaryCompareReturnsNullDeltaForNutritionGramsWhenBothPeriodsHaveNoData() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
        when(workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(any(), any(), any()))
                .thenReturn(List.of());
        when(nutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(any(), any(), any()))
                .thenReturn(List.of(
                        nutritionDay(userId, "2026-06-18", 0, null, null, null, null, null)));

        AnalyticsCompareResponse response = compareService(
                workoutRepository,
                nutritionRepository,
                Clock.fixed(Instant.parse("2026-06-18T10:00:00Z"), ZoneOffset.UTC))
                .getSummaryCompare(principal(userId), ComparePeriod.WEEK);

        assertThat(response.nutrition().totalProteinGrams())
                .extracting(
                        AnalyticsCompareResponse.MetricComparison::current,
                        AnalyticsCompareResponse.MetricComparison::previous,
                        AnalyticsCompareResponse.MetricComparison::delta,
                        AnalyticsCompareResponse.MetricComparison::deltaPct)
                .containsExactly(null, null, null, null);
    }

    @Test
    void shouldAggregateBodyTrendsByIsoWeekStartUsingLastValidValues() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-06-14T08:00:00Z"), ZoneOffset.UTC);
        AnalyticsUserDashboardSnapshotRepository dashboardRepository = mock(AnalyticsUserDashboardSnapshotRepository.class);
        AnalyticsUserGoalProgressRepository goalRepository = mock(AnalyticsUserGoalProgressRepository.class);
        AnalyticsUserDailyBodyRepository bodyRepository = mock(AnalyticsUserDailyBodyRepository.class);
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        when(bodyRepository.findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(
                userId, LocalDate.parse("2026-06-02"), LocalDate.parse("2026-06-10")))
                .thenReturn(List.of(
                        bodyDay(userId, "2026-06-02", "80.00", "20.00", "90.00", "25.00", "0.00", null),
                        bodyDay(userId, "2026-06-04", "79.50", null, "89.00", "24.80", "-0.50", "-0.50"),
                        bodyDay(userId, "2026-06-08", "79.00", "19.50", null, "24.60", "-1.00", "-0.50"),
                        bodyDay(userId, "2026-06-10", null, "19.20", "88.00", null, null, null)));

        AnalyticsServiceImpl service = new AnalyticsServiceImpl(
                dashboardRepository, goalRepository, bodyRepository, workoutRepository, nutritionRepository,
                mock(AnalyticsUserStreakRollupRepository.class), mock(AnalyticsUserWorkoutRecordsRepository.class),
                fixedClock, analyticsProperties());

        var response = service.getBodyTrends(
                new CurrentUserPrincipal(userId, "alice@example.com", "Alice", "USER"),
                LocalDate.parse("2026-06-02"),
                LocalDate.parse("2026-06-10"),
                BodyTrendBucket.WEEK);

        assertThat(response.points()).hasSize(2);
        assertThat(response.points().get(0))
                .extracting(
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::date,
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::weightKg,
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::bodyFatRate,
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::waistCm,
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::bmi,
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::weightDeltaFromStart,
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::weightDeltaFromPrevious)
                .containsExactly(LocalDate.parse("2026-06-01"), 79.5, 20.0, 89.0, 24.8, -0.5, null);
        assertThat(response.points().get(1))
                .extracting(
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::date,
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::weightKg,
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::bodyFatRate,
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::waistCm,
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::bmi,
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::weightDeltaFromStart,
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::weightDeltaFromPrevious)
                .containsExactly(LocalDate.parse("2026-06-08"), 79.0, 19.2, 88.0, 24.6, -1.0, -0.5);
    }

    @Test
    void shouldAggregateBodyTrendsByMonthStartAndRecomputeBucketDeltas() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-02-20T08:00:00Z"), ZoneOffset.UTC);
        AnalyticsUserDashboardSnapshotRepository dashboardRepository = mock(AnalyticsUserDashboardSnapshotRepository.class);
        AnalyticsUserGoalProgressRepository goalRepository = mock(AnalyticsUserGoalProgressRepository.class);
        AnalyticsUserDailyBodyRepository bodyRepository = mock(AnalyticsUserDailyBodyRepository.class);
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        when(bodyRepository.findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(
                userId, LocalDate.parse("2026-01-05"), LocalDate.parse("2026-02-20")))
                .thenReturn(List.of(
                        bodyDay(userId, "2026-01-05", "82.00", "20.00", "92.00", "26.00", "0.00", null),
                        bodyDay(userId, "2026-01-31", "81.00", "19.60", "91.00", "25.50", "-1.00", "-1.00"),
                        bodyDay(userId, "2026-02-01", "80.80", null, "90.50", "25.30", "-1.20", "-0.20"),
                        bodyDay(userId, "2026-02-20", "80.00", "19.10", "90.00", "25.00", "-2.00", "-0.80")));

        AnalyticsServiceImpl service = new AnalyticsServiceImpl(
                dashboardRepository, goalRepository, bodyRepository, workoutRepository, nutritionRepository,
                mock(AnalyticsUserStreakRollupRepository.class), mock(AnalyticsUserWorkoutRecordsRepository.class),
                fixedClock, analyticsProperties());

        var response = service.getBodyTrends(
                new CurrentUserPrincipal(userId, "alice@example.com", "Alice", "USER"),
                LocalDate.parse("2026-01-05"),
                LocalDate.parse("2026-02-20"),
                BodyTrendBucket.MONTH);

        assertThat(response.points()).hasSize(2);
        assertThat(response.points().get(0))
                .extracting(
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::date,
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::weightKg,
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::bodyFatRate,
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::waistCm,
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::bmi,
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::weightDeltaFromStart,
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::weightDeltaFromPrevious)
                .containsExactly(LocalDate.parse("2026-01-01"), 81.0, 19.6, 91.0, 25.5, -1.0, null);
        assertThat(response.points().get(1))
                .extracting(
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::date,
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::weightKg,
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::bodyFatRate,
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::waistCm,
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::bmi,
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::weightDeltaFromStart,
                        AnalyticsBodyTrendsResponse.BodyTrendPoint::weightDeltaFromPrevious)
                .containsExactly(LocalDate.parse("2026-02-01"), 80.0, 19.1, 90.0, 25.0, -2.0, -1.0);
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
        when(workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateDesc(
                userId, LocalDate.parse("2026-06-18").minusDays(400), LocalDate.parse("2026-06-18")))
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

    @Test
    void shouldReturnZeroedStreaksForUserWithoutData() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        AnalyticsUserDashboardSnapshotRepository dashboardRepository = mock(AnalyticsUserDashboardSnapshotRepository.class);
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserStreakRollupRepository streakRollupRepository = mock(AnalyticsUserStreakRollupRepository.class);
        when(dashboardRepository.findById(userId)).thenReturn(Optional.empty());
        when(workoutRepository.findTopByUserIdAndCompletedWorkoutCountGreaterThanOrderByActivityDateDesc(userId, 0))
                .thenReturn(Optional.empty());
        when(streakRollupRepository.findById(userId)).thenReturn(Optional.empty());

        AnalyticsStreaksResponse response = streaksService(dashboardRepository, workoutRepository, streakRollupRepository)
                .getStreaks(principal(userId));

        assertThat(response.currentStreakDays()).isZero();
        assertThat(response.bestStreakDays()).isZero();
        assertThat(response.bestStreakFrom()).isNull();
        assertThat(response.bestStreakTo()).isNull();
        assertThat(response.lastActiveDate()).isNull();
        assertThat(response.timezone()).isEqualTo("UTC");
    }

    @Test
    void shouldReturnSnapshotStreakAndMaterializedBestStreak() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        AnalyticsUserDashboardSnapshotRepository dashboardRepository = mock(AnalyticsUserDashboardSnapshotRepository.class);
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserStreakRollupRepository streakRollupRepository = mock(AnalyticsUserStreakRollupRepository.class);

        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setUserId(userId);
        snapshot.setTimezone("Asia/Singapore");
        snapshot.setActiveStreakDays(6);
        when(dashboardRepository.findById(userId)).thenReturn(Optional.of(snapshot));
        when(workoutRepository.findTopByUserIdAndCompletedWorkoutCountGreaterThanOrderByActivityDateDesc(userId, 0))
                .thenReturn(Optional.of(workoutDay(userId, "2026-07-07", 1, 40)));

        AnalyticsUserStreakRollupEntity rollup = new AnalyticsUserStreakRollupEntity();
        rollup.setUserId(userId);
        rollup.setBestStreakDays(14);
        rollup.setBestStreakFrom(LocalDate.parse("2026-03-02"));
        rollup.setBestStreakTo(LocalDate.parse("2026-03-15"));
        when(streakRollupRepository.findById(userId)).thenReturn(Optional.of(rollup));

        AnalyticsStreaksResponse response = streaksService(dashboardRepository, workoutRepository, streakRollupRepository)
                .getStreaks(principal(userId));

        assertThat(response.currentStreakDays()).isEqualTo(6);
        assertThat(response.bestStreakDays()).isEqualTo(14);
        assertThat(response.bestStreakFrom()).isEqualTo(LocalDate.parse("2026-03-02"));
        assertThat(response.bestStreakTo()).isEqualTo(LocalDate.parse("2026-03-15"));
        assertThat(response.lastActiveDate()).isEqualTo(LocalDate.parse("2026-07-07"));
        assertThat(response.timezone()).isEqualTo("Asia/Singapore");
    }

    @Test
    void shouldLiftBestStreakWhenLiveCurrentStreakExceedsMaterializedBest() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        AnalyticsUserDashboardSnapshotRepository dashboardRepository = mock(AnalyticsUserDashboardSnapshotRepository.class);
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserStreakRollupRepository streakRollupRepository = mock(AnalyticsUserStreakRollupRepository.class);

        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setUserId(userId);
        snapshot.setActiveStreakDays(3);
        when(dashboardRepository.findById(userId)).thenReturn(Optional.of(snapshot));
        when(workoutRepository.findTopByUserIdAndCompletedWorkoutCountGreaterThanOrderByActivityDateDesc(userId, 0))
                .thenReturn(Optional.of(workoutDay(userId, "2026-06-14", 1, 30)));

        AnalyticsUserStreakRollupEntity rollup = new AnalyticsUserStreakRollupEntity();
        rollup.setUserId(userId);
        rollup.setBestStreakDays(2);
        rollup.setBestStreakFrom(LocalDate.parse("2026-05-01"));
        rollup.setBestStreakTo(LocalDate.parse("2026-05-02"));
        when(streakRollupRepository.findById(userId)).thenReturn(Optional.of(rollup));

        AnalyticsStreaksResponse response = streaksService(dashboardRepository, workoutRepository, streakRollupRepository)
                .getStreaks(principal(userId));

        assertThat(response.currentStreakDays()).isEqualTo(3);
        assertThat(response.bestStreakDays()).isEqualTo(3);
        assertThat(response.bestStreakFrom()).isEqualTo(LocalDate.parse("2026-06-12"));
        assertThat(response.bestStreakTo()).isEqualTo(LocalDate.parse("2026-06-14"));
    }

    @Test
    void shouldReportStreakStateOnUserTimezoneDatesAcrossUtcMidnight() {
        // The projection keys activity days by the user's timezone, so right after midnight in
        // Asia/Singapore (still the previous day in UTC) the last active date is the Singapore date
        // and the still-alive one-day streak is surfaced unchanged.
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        AnalyticsUserDashboardSnapshotRepository dashboardRepository = mock(AnalyticsUserDashboardSnapshotRepository.class);
        AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserStreakRollupRepository streakRollupRepository = mock(AnalyticsUserStreakRollupRepository.class);

        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setUserId(userId);
        snapshot.setTimezone("Asia/Singapore");
        snapshot.setActiveStreakDays(1);
        when(dashboardRepository.findById(userId)).thenReturn(Optional.of(snapshot));
        when(workoutRepository.findTopByUserIdAndCompletedWorkoutCountGreaterThanOrderByActivityDateDesc(userId, 0))
                .thenReturn(Optional.of(workoutDay(userId, "2026-06-15", 1, 30)));
        when(streakRollupRepository.findById(userId)).thenReturn(Optional.empty());

        AnalyticsStreaksResponse response = streaksService(dashboardRepository, workoutRepository, streakRollupRepository)
                .getStreaks(principal(userId));

        assertThat(response.currentStreakDays()).isEqualTo(1);
        assertThat(response.bestStreakDays()).isEqualTo(1);
        assertThat(response.bestStreakFrom()).isEqualTo(LocalDate.parse("2026-06-15"));
        assertThat(response.bestStreakTo()).isEqualTo(LocalDate.parse("2026-06-15"));
        assertThat(response.lastActiveDate()).isEqualTo(LocalDate.parse("2026-06-15"));
        assertThat(response.timezone()).isEqualTo("Asia/Singapore");
    }

    @Test
    void shouldReturnFullyLockedAchievementListForUserWithoutData() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        AchievementFixtures fixtures = new AchievementFixtures(userId);

        AnalyticsAchievementsResponse response = fixtures.service().getAchievements(principal(userId));

        assertThat(response.achievements())
                .extracting(AnalyticsAchievementsResponse.AchievementView::code)
                .containsExactly(
                        "FIRST_WORKOUT", "WORKOUTS_10", "WORKOUTS_50", "WORKOUTS_100",
                        "STREAK_7", "STREAK_30",
                        "FIRST_MEAL_LOGGED", "FIRST_WEIGHT_LOGGED", "PR_FIRST");
        assertThat(response.achievements())
                .allSatisfy(achievement -> {
                    assertThat(achievement.unlocked()).isFalse();
                    assertThat(achievement.unlockedAt()).isNull();
                    assertThat(achievement.progress()).isZero();
                    assertThat(achievement.title()).isNotBlank();
                });
    }

    @Test
    void shouldDeriveAchievementUnlocksAndProgressOnUserTimezoneDays() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        AchievementFixtures fixtures = new AchievementFixtures(userId);

        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setUserId(userId);
        snapshot.setTimezone("Asia/Singapore");
        snapshot.setActiveStreakDays(3);
        when(fixtures.dashboardRepository.findById(userId)).thenReturn(Optional.of(snapshot));
        when(fixtures.workoutRepository.findByUserIdOrderByActivityDateAsc(userId)).thenReturn(List.of(
                workoutDay(userId, "2026-05-01", 1, 30),
                workoutDay(userId, "2026-05-02", 9, 270),
                workoutDay(userId, "2026-05-03", 2, 60)));

        AnalyticsUserStreakRollupEntity rollup = new AnalyticsUserStreakRollupEntity();
        rollup.setUserId(userId);
        rollup.setBestStreakDays(14);
        rollup.setBestStreakFrom(LocalDate.parse("2026-03-02"));
        rollup.setBestStreakTo(LocalDate.parse("2026-03-15"));
        when(fixtures.streakRollupRepository.findById(userId)).thenReturn(Optional.of(rollup));

        when(fixtures.nutritionRepository.findTopByUserIdAndMealCountGreaterThanOrderByActivityDateAsc(userId, 0))
                .thenReturn(Optional.of(nutritionDay(userId, "2026-05-01", 1800, "120.00", "200.00", "60.00", 2100, "150.00")));
        when(fixtures.bodyRepository.findTopByUserIdAndWeightKgIsNotNullOrderByMetricDateAsc(userId))
                .thenReturn(Optional.of(bodyDay(userId, "2026-05-02", "78.40", "19.80", "85.50", "24.70", "-1.60", "-0.20")));

        AnalyticsUserWorkoutRecordsEntity records = new AnalyticsUserWorkoutRecordsEntity();
        records.setUserId(userId);
        records.setPersonalRecordCount(2);
        records.setFirstRecordAt(Instant.parse("2026-05-01T10:00:00Z"));
        when(fixtures.workoutRecordsRepository.findById(userId)).thenReturn(Optional.of(records));

        AnalyticsAchievementsResponse response = fixtures.service().getAchievements(principal(userId));

        assertThat(response.achievements())
                .extracting(AnalyticsAchievementsResponse.AchievementView::code,
                        AnalyticsAchievementsResponse.AchievementView::unlocked,
                        AnalyticsAchievementsResponse.AchievementView::unlockedAt,
                        AnalyticsAchievementsResponse.AchievementView::progress)
                .containsExactly(
                        // Start-of-day in Asia/Singapore is 16:00 UTC on the previous calendar day.
                        tuple("FIRST_WORKOUT", true, Instant.parse("2026-04-30T16:00:00Z"), 1.0),
                        tuple("WORKOUTS_10", true, Instant.parse("2026-05-01T16:00:00Z"), 1.0),
                        tuple("WORKOUTS_50", false, null, 0.24),
                        tuple("WORKOUTS_100", false, null, 0.12),
                        tuple("STREAK_7", true, Instant.parse("2026-03-07T16:00:00Z"), 1.0),
                        tuple("STREAK_30", false, null, 0.4667),
                        tuple("FIRST_MEAL_LOGGED", true, Instant.parse("2026-04-30T16:00:00Z"), 1.0),
                        tuple("FIRST_WEIGHT_LOGGED", true, Instant.parse("2026-05-01T16:00:00Z"), 1.0),
                        tuple("PR_FIRST", true, Instant.parse("2026-05-01T10:00:00Z"), 1.0));
    }

    @Test
    void shouldUnlockStreakAchievementFromLiveStreakWhenRollupLags() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        AchievementFixtures fixtures = new AchievementFixtures(userId);

        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setUserId(userId);
        snapshot.setActiveStreakDays(7);
        when(fixtures.dashboardRepository.findById(userId)).thenReturn(Optional.of(snapshot));
        when(fixtures.workoutRepository.findByUserIdOrderByActivityDateAsc(userId)).thenReturn(List.of(
                workoutDay(userId, "2026-06-08", 1, 30),
                workoutDay(userId, "2026-06-09", 1, 30),
                workoutDay(userId, "2026-06-10", 1, 30),
                workoutDay(userId, "2026-06-11", 1, 30),
                workoutDay(userId, "2026-06-12", 1, 30),
                workoutDay(userId, "2026-06-13", 1, 30),
                workoutDay(userId, "2026-06-14", 1, 30)));

        AnalyticsAchievementsResponse response = fixtures.service().getAchievements(principal(userId));

        AnalyticsAchievementsResponse.AchievementView streak7 = achievement(response, "STREAK_7");
        assertThat(streak7.unlocked()).isTrue();
        assertThat(streak7.unlockedAt()).isEqualTo(Instant.parse("2026-06-14T00:00:00Z"));
        AnalyticsAchievementsResponse.AchievementView streak30 = achievement(response, "STREAK_30");
        assertThat(streak30.unlocked()).isFalse();
        assertThat(streak30.progress()).isEqualTo(0.2333);
    }

    private AnalyticsAchievementsResponse.AchievementView achievement(AnalyticsAchievementsResponse response,
                                                                      String code) {
        return response.achievements().stream()
                .filter(view -> code.equals(view.code()))
                .findFirst()
                .orElseThrow();
    }

    private final class AchievementFixtures {
        private final AnalyticsUserDashboardSnapshotRepository dashboardRepository =
                mock(AnalyticsUserDashboardSnapshotRepository.class);
        private final AnalyticsUserDailyWorkoutRepository workoutRepository =
                mock(AnalyticsUserDailyWorkoutRepository.class);
        private final AnalyticsUserDailyNutritionRepository nutritionRepository =
                mock(AnalyticsUserDailyNutritionRepository.class);
        private final AnalyticsUserDailyBodyRepository bodyRepository =
                mock(AnalyticsUserDailyBodyRepository.class);
        private final AnalyticsUserStreakRollupRepository streakRollupRepository =
                mock(AnalyticsUserStreakRollupRepository.class);
        private final AnalyticsUserWorkoutRecordsRepository workoutRecordsRepository =
                mock(AnalyticsUserWorkoutRecordsRepository.class);

        private AchievementFixtures(UUID userId) {
            when(dashboardRepository.findById(userId)).thenReturn(Optional.empty());
            when(workoutRepository.findByUserIdOrderByActivityDateAsc(userId)).thenReturn(List.of());
            when(streakRollupRepository.findById(userId)).thenReturn(Optional.empty());
            when(nutritionRepository.findTopByUserIdAndMealCountGreaterThanOrderByActivityDateAsc(userId, 0))
                    .thenReturn(Optional.empty());
            when(bodyRepository.findTopByUserIdAndWeightKgIsNotNullOrderByMetricDateAsc(userId))
                    .thenReturn(Optional.empty());
            when(workoutRecordsRepository.findById(userId)).thenReturn(Optional.empty());
        }

        private AnalyticsServiceImpl service() {
            return new AnalyticsServiceImpl(
                    dashboardRepository,
                    mock(AnalyticsUserGoalProgressRepository.class),
                    bodyRepository,
                    workoutRepository,
                    nutritionRepository,
                    streakRollupRepository,
                    workoutRecordsRepository,
                    Clock.fixed(Instant.parse("2026-06-14T08:00:00Z"), ZoneOffset.UTC),
                    analyticsProperties());
        }
    }

    @Test
    void shouldReturnZeroedWeeklyReportForEmptyWeek() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        WeeklyReportFixtures fixtures = new WeeklyReportFixtures(userId);

        AnalyticsWeeklyReportResponse response = fixtures
                .service(Clock.fixed(Instant.parse("2026-06-14T08:00:00Z"), ZoneOffset.UTC))
                .getWeeklyReport(principal(userId), LocalDate.parse("2026-06-01"));

        assertThat(response.from()).isEqualTo(LocalDate.parse("2026-06-01"));
        assertThat(response.to()).isEqualTo(LocalDate.parse("2026-06-07"));
        assertThat(response.workoutCount()).isZero();
        assertThat(response.workoutMinutes()).isZero();
        assertThat(response.caloriesBurned()).isZero();
        assertThat(response.avgDailyCaloriesConsumed()).isNull();
        assertThat(response.proteinTargetHitDays()).isZero();
        assertThat(response.weightDeltaKg()).isNull();
        assertThat(response.vsPreviousWeek().workoutCount()).isZero();
        assertThat(response.vsPreviousWeek().workoutMinutes()).isZero();
    }

    @Test
    void shouldAggregateWeeklyReportWithWeekOverWeekDeltas() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        WeeklyReportFixtures fixtures = new WeeklyReportFixtures(userId);
        LocalDate weekStart = LocalDate.parse("2026-06-08");

        when(fixtures.workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                userId, weekStart, LocalDate.parse("2026-06-14")))
                .thenReturn(List.of(
                        workoutDay(userId, "2026-06-08", 2, 60, 500, 8),
                        workoutDay(userId, "2026-06-10", 2, 70, 450, 9)));
        when(fixtures.workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                userId, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-07")))
                .thenReturn(List.of(workoutDay(userId, "2026-06-02", 3, 98, 700, 12)));

        AnalyticsUserDailyNutritionEntity mondayNutrition =
                nutritionDay(userId, "2026-06-08", 2000, "155.00", null, null, 2100, "150.00");
        mondayNutrition.setMealCount(3);
        mondayNutrition.setMacroAdherenceScore(new BigDecimal("1.0333"));
        AnalyticsUserDailyNutritionEntity tuesdayNutrition =
                nutritionDay(userId, "2026-06-09", 2100, "120.00", null, null, 2100, "150.00");
        tuesdayNutrition.setMealCount(2);
        tuesdayNutrition.setMacroAdherenceScore(new BigDecimal("0.8000"));
        AnalyticsUserDailyNutritionEntity untrackedDay =
                nutritionDay(userId, "2026-06-10", 0, null, null, null, 2100, "150.00");
        untrackedDay.setMealCount(0);
        when(fixtures.nutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                userId, weekStart, LocalDate.parse("2026-06-14")))
                .thenReturn(List.of(mondayNutrition, tuesdayNutrition, untrackedDay));

        when(fixtures.bodyRepository.findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(
                userId, weekStart, LocalDate.parse("2026-06-14")))
                .thenReturn(List.of(
                        bodyDay(userId, "2026-06-08", "80.40", null, null, null, null, null),
                        bodyDay(userId, "2026-06-12", "80.00", null, null, null, null, null)));

        AnalyticsWeeklyReportResponse response = fixtures
                .service(Clock.fixed(Instant.parse("2026-06-14T08:00:00Z"), ZoneOffset.UTC))
                .getWeeklyReport(principal(userId), weekStart);

        assertThat(response.workoutCount()).isEqualTo(4);
        assertThat(response.workoutMinutes()).isEqualTo(130);
        assertThat(response.caloriesBurned()).isEqualTo(950);
        assertThat(response.avgDailyCaloriesConsumed()).isEqualTo(2050);
        assertThat(response.proteinTargetHitDays()).isEqualTo(1);
        assertThat(response.weightDeltaKg()).isEqualTo(-0.4);
        assertThat(response.vsPreviousWeek().workoutCount()).isEqualTo(1);
        assertThat(response.vsPreviousWeek().workoutMinutes()).isEqualTo(32);
    }

    @Test
    void shouldRejectWeekStartThatIsNotAMonday() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        WeeklyReportFixtures fixtures = new WeeklyReportFixtures(userId);
        AnalyticsServiceImpl service = fixtures
                .service(Clock.fixed(Instant.parse("2026-06-14T08:00:00Z"), ZoneOffset.UTC));
        CurrentUserPrincipal principal = principal(userId);
        LocalDate wednesday = LocalDate.parse("2026-06-03");

        assertThatThrownBy(() -> service.getWeeklyReport(principal, wednesday))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("weekStart must be a Monday (ISO week start)");
        verify(fixtures.workoutRepository, never())
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(any(), any(), any());
    }

    @Test
    void shouldDefaultWeekStartToCurrentMondayInUserTimezone() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        WeeklyReportFixtures fixtures = new WeeklyReportFixtures(userId);
        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setUserId(userId);
        snapshot.setTimezone("Asia/Singapore");
        when(fixtures.dashboardRepository.findById(userId)).thenReturn(Optional.of(snapshot));

        // 2026-07-12T17:30Z is Sunday evening in UTC but already Monday 2026-07-13 01:30 in Singapore,
        // so the default week must be the Singapore week, not the UTC one.
        AnalyticsWeeklyReportResponse response = fixtures
                .service(Clock.fixed(Instant.parse("2026-07-12T17:30:00Z"), ZoneOffset.UTC))
                .getWeeklyReport(principal(userId), null);

        assertThat(response.from()).isEqualTo(LocalDate.parse("2026-07-13"));
        assertThat(response.to()).isEqualTo(LocalDate.parse("2026-07-19"));
    }

    private final class WeeklyReportFixtures {
        private final AnalyticsUserDashboardSnapshotRepository dashboardRepository =
                mock(AnalyticsUserDashboardSnapshotRepository.class);
        private final AnalyticsUserDailyWorkoutRepository workoutRepository =
                mock(AnalyticsUserDailyWorkoutRepository.class);
        private final AnalyticsUserDailyNutritionRepository nutritionRepository =
                mock(AnalyticsUserDailyNutritionRepository.class);
        private final AnalyticsUserDailyBodyRepository bodyRepository =
                mock(AnalyticsUserDailyBodyRepository.class);

        private WeeklyReportFixtures(UUID userId) {
            when(dashboardRepository.findById(userId)).thenReturn(Optional.empty());
            when(workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(any(), any(), any()))
                    .thenReturn(List.of());
            when(nutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(any(), any(), any()))
                    .thenReturn(List.of());
            when(bodyRepository.findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(any(), any(), any()))
                    .thenReturn(List.of());
        }

        private AnalyticsServiceImpl service(Clock clock) {
            return new AnalyticsServiceImpl(
                    dashboardRepository,
                    mock(AnalyticsUserGoalProgressRepository.class),
                    bodyRepository,
                    workoutRepository,
                    nutritionRepository,
                    mock(AnalyticsUserStreakRollupRepository.class),
                    mock(AnalyticsUserWorkoutRecordsRepository.class),
                    clock,
                    analyticsProperties());
        }
    }

    private void assertTrendRangePasses(BodyTrendBucket bucket, int days) {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        LocalDate from = LocalDate.parse("2026-01-01");
        LocalDate to = from.plusDays(days);
        AnalyticsUserDailyBodyRepository bodyRepository = mock(AnalyticsUserDailyBodyRepository.class);
        when(bodyRepository.findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(userId, from, to))
                .thenReturn(List.of());

        AnalyticsServiceImpl service = bodyTrendService(bodyRepository);

        AnalyticsBodyTrendsResponse response = service.getBodyTrends(principal(userId), from, to, bucket);

        assertThat(response.points()).isEmpty();
        verify(bodyRepository).findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(userId, from, to);
    }

    private void assertTrendRangeRejected(BodyTrendBucket bucket, int cap) {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        LocalDate from = LocalDate.parse("2026-01-01");
        AnalyticsUserDailyBodyRepository bodyRepository = mock(AnalyticsUserDailyBodyRepository.class);
        AnalyticsServiceImpl service = bodyTrendService(bodyRepository);

        assertThatThrownBy(() -> service.getBodyTrends(principal(userId), from, from.plusDays(cap + 1L), bucket))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Date range for " + bucket.name() + " bucket must not exceed " + cap + " days");
        verify(bodyRepository, never()).findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(any(), any(), any());
    }

    private AnalyticsServiceImpl bodyTrendService(AnalyticsUserDailyBodyRepository bodyRepository) {
        return new AnalyticsServiceImpl(
                mock(AnalyticsUserDashboardSnapshotRepository.class),
                mock(AnalyticsUserGoalProgressRepository.class),
                bodyRepository,
                mock(AnalyticsUserDailyWorkoutRepository.class),
                mock(AnalyticsUserDailyNutritionRepository.class),
                mock(AnalyticsUserStreakRollupRepository.class),
                mock(AnalyticsUserWorkoutRecordsRepository.class),
                Clock.fixed(Instant.parse("2026-06-14T08:00:00Z"), ZoneOffset.UTC),
                analyticsProperties());
    }

    private AnalyticsServiceImpl workoutSummaryService(AnalyticsUserDailyWorkoutRepository workoutRepository) {
        return new AnalyticsServiceImpl(
                mock(AnalyticsUserDashboardSnapshotRepository.class),
                mock(AnalyticsUserGoalProgressRepository.class),
                mock(AnalyticsUserDailyBodyRepository.class),
                workoutRepository,
                mock(AnalyticsUserDailyNutritionRepository.class),
                mock(AnalyticsUserStreakRollupRepository.class),
                mock(AnalyticsUserWorkoutRecordsRepository.class),
                Clock.fixed(Instant.parse("2026-06-14T08:00:00Z"), ZoneOffset.UTC),
                analyticsProperties());
    }

    private AnalyticsServiceImpl workoutSeriesService(AnalyticsUserDailyWorkoutRepository workoutRepository) {
        return new AnalyticsServiceImpl(
                mock(AnalyticsUserDashboardSnapshotRepository.class),
                mock(AnalyticsUserGoalProgressRepository.class),
                mock(AnalyticsUserDailyBodyRepository.class),
                workoutRepository,
                mock(AnalyticsUserDailyNutritionRepository.class),
                mock(AnalyticsUserStreakRollupRepository.class),
                mock(AnalyticsUserWorkoutRecordsRepository.class),
                Clock.fixed(Instant.parse("2026-06-14T08:00:00Z"), ZoneOffset.UTC),
                analyticsProperties());
    }

    private AnalyticsServiceImpl nutritionSeriesService(AnalyticsUserDailyNutritionRepository nutritionRepository) {
        return new AnalyticsServiceImpl(
                mock(AnalyticsUserDashboardSnapshotRepository.class),
                mock(AnalyticsUserGoalProgressRepository.class),
                mock(AnalyticsUserDailyBodyRepository.class),
                mock(AnalyticsUserDailyWorkoutRepository.class),
                nutritionRepository,
                mock(AnalyticsUserStreakRollupRepository.class),
                mock(AnalyticsUserWorkoutRecordsRepository.class),
                Clock.fixed(Instant.parse("2026-06-14T08:00:00Z"), ZoneOffset.UTC),
                analyticsProperties());
    }

    private AnalyticsServiceImpl compareService(AnalyticsUserDailyWorkoutRepository workoutRepository,
                                                AnalyticsUserDailyNutritionRepository nutritionRepository,
                                                Clock clock) {
        return new AnalyticsServiceImpl(
                mock(AnalyticsUserDashboardSnapshotRepository.class),
                mock(AnalyticsUserGoalProgressRepository.class),
                mock(AnalyticsUserDailyBodyRepository.class),
                workoutRepository,
                nutritionRepository,
                mock(AnalyticsUserStreakRollupRepository.class),
                mock(AnalyticsUserWorkoutRecordsRepository.class),
                clock,
                analyticsProperties());
    }

    private AnalyticsServiceImpl streaksService(AnalyticsUserDashboardSnapshotRepository dashboardRepository,
                                                AnalyticsUserDailyWorkoutRepository workoutRepository,
                                                AnalyticsUserStreakRollupRepository streakRollupRepository) {
        return new AnalyticsServiceImpl(
                dashboardRepository,
                mock(AnalyticsUserGoalProgressRepository.class),
                mock(AnalyticsUserDailyBodyRepository.class),
                workoutRepository,
                mock(AnalyticsUserDailyNutritionRepository.class),
                streakRollupRepository,
                mock(AnalyticsUserWorkoutRecordsRepository.class),
                Clock.fixed(Instant.parse("2026-06-14T08:00:00Z"), ZoneOffset.UTC),
                analyticsProperties());
    }

    private CurrentUserPrincipal principal(UUID userId) {
        return new CurrentUserPrincipal(userId, "alice@example.com", "Alice", "USER");
    }

    private AnalyticsProperties analyticsProperties() {
        return new AnalyticsProperties(
                new AnalyticsProperties.Trends(new AnalyticsProperties.MaxRangeDays(370, 1095, 1830)));
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
                mock(com.fitsync.analytics.messaging.AnalyticsEventPublisher.class),
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

    private AnalyticsUserDailyWorkoutEntity workoutDay(UUID userId,
                                                       String date,
                                                       int count,
                                                       int minutes,
                                                       int calories,
                                                       int exercises) {
        AnalyticsUserDailyWorkoutEntity workout = new AnalyticsUserDailyWorkoutEntity();
        workout.setUserId(userId);
        workout.setActivityDate(LocalDate.parse(date));
        workout.setCompletedWorkoutCount(count);
        workout.setTotalWorkoutMinutes(minutes);
        workout.setTotalCaloriesBurned(calories);
        workout.setTotalExerciseCount(exercises);
        return workout;
    }

    private AnalyticsUserDailyNutritionEntity nutritionDay(UUID userId,
                                                           String date,
                                                           Integer totalCaloriesConsumed,
                                                           String totalProteinGrams,
                                                           String totalCarbsGrams,
                                                           String totalFatGrams,
                                                           Integer calorieTarget,
                                                           String proteinTargetGrams) {
        AnalyticsUserDailyNutritionEntity nutrition = new AnalyticsUserDailyNutritionEntity();
        nutrition.setUserId(userId);
        nutrition.setActivityDate(LocalDate.parse(date));
        nutrition.setTotalCaloriesConsumed(totalCaloriesConsumed);
        nutrition.setTotalProteinGrams(decimal(totalProteinGrams));
        nutrition.setTotalCarbsGrams(decimal(totalCarbsGrams));
        nutrition.setTotalFatGrams(decimal(totalFatGrams));
        nutrition.setCalorieTarget(calorieTarget);
        nutrition.setProteinTargetGrams(decimal(proteinTargetGrams));
        return nutrition;
    }

    private AnalyticsUserDailyBodyEntity bodyDay(UUID userId,
                                                 String date,
                                                 String weightKg,
                                                 String bodyFatRate,
                                                 String waistCm,
                                                 String bmi,
                                                 String weightDeltaFromStart,
                                                 String weightDeltaFromPrevious) {
        AnalyticsUserDailyBodyEntity body = new AnalyticsUserDailyBodyEntity();
        body.setUserId(userId);
        body.setMetricDate(LocalDate.parse(date));
        body.setWeightKg(decimal(weightKg));
        body.setBodyFatRate(decimal(bodyFatRate));
        body.setWaistCm(decimal(waistCm));
        body.setBmi(decimal(bmi));
        body.setWeightDeltaFromStart(decimal(weightDeltaFromStart));
        body.setWeightDeltaFromPrevious(decimal(weightDeltaFromPrevious));
        return body;
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
