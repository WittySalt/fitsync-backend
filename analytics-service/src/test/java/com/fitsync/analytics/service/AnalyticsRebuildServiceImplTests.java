package com.fitsync.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.analytics.dto.AnalyticsRebuildResponse;
import com.fitsync.analytics.entity.AnalyticsMealContributionEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyBodyEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyWorkoutEntity;
import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import com.fitsync.analytics.entity.AnalyticsUserGoalProgressEntity;
import com.fitsync.analytics.entity.AnalyticsWorkoutContributionEntity;
import com.fitsync.analytics.messaging.AnalyticsEventPublisher;
import com.fitsync.analytics.repository.AnalyticsMealContributionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import com.fitsync.analytics.repository.AnalyticsWorkoutContributionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AnalyticsRebuildServiceImplTests {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-06-15T08:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-06-15");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void rebuildDashboardMapsLatestMetricsTodaysNutritionGoalsAndWorkoutStreak() {
        Fixture fixture = newFixture();
        AnalyticsUserDashboardSnapshotEntity snapshot = dashboardSnapshot(6);
        when(fixture.dashboardSnapshotRepository().findById(USER_ID)).thenReturn(Optional.of(snapshot));
        when(fixture.dailyBodyRepository().findTopByUserIdOrderByMetricDateDesc(USER_ID))
                .thenReturn(Optional.of(bodyMetrics()));
        when(fixture.dailyNutritionRepository().findByUserIdAndActivityDate(USER_ID, TODAY))
                .thenReturn(Optional.of(nutritionRow(700, "48.50")));
        when(fixture.dailyWorkoutRepository()
                .findByUserIdAndActivityDateBetweenOrderByActivityDateDesc(USER_ID, TODAY.minusDays(6), TODAY))
                .thenReturn(consecutiveWorkoutRows(7));
        when(fixture.goalProgressRepository().findByUserIdOrderByTargetDateAscGoalIdAsc(USER_ID))
                .thenReturn(List.of(
                        goal("calories", "active", "2100.00"),
                        goal("PROTEIN", "ACTIVE", "150.00"),
                        goal("CALORIES", "COMPLETED", "9999.00")));

        AnalyticsRebuildResponse response = fixture.service().rebuildDashboard(USER_ID);

        ArgumentCaptor<AnalyticsUserDashboardSnapshotEntity> captor =
                ArgumentCaptor.forClass(AnalyticsUserDashboardSnapshotEntity.class);
        verify(fixture.dashboardSnapshotRepository()).save(captor.capture());
        AnalyticsUserDashboardSnapshotEntity saved = captor.getValue();
        assertThat(response.scope()).isEqualTo("dashboard");
        assertThat(response.rebuiltAt()).isEqualTo(NOW);
        assertThat(saved.getLatestWeightKg()).isEqualByComparingTo("78.40");
        assertThat(saved.getLatestBodyFatRate()).isEqualByComparingTo("19.80");
        assertThat(saved.getBmi()).isEqualByComparingTo("24.70");
        assertThat(saved.getWeightChange30d()).isEqualByComparingTo("-1.60");
        assertThat(saved.getTodayCaloriesConsumed()).isEqualTo(700);
        assertThat(saved.getTodayCaloriesTarget()).isEqualTo(2100);
        assertThat(saved.getTodayProteinGrams()).isEqualByComparingTo("48.50");
        assertThat(saved.getTodayProteinTargetGrams()).isEqualByComparingTo("150.00");
        assertThat(saved.getTodayWorkoutCount()).isEqualTo(1);
        assertThat(saved.getWeekWorkoutCount()).isEqualTo(7);
        assertThat(saved.getWeekWorkoutMinutes()).isEqualTo(315);
        assertThat(saved.getActiveStreakDays()).isEqualTo(7);
        verify(fixture.analyticsEventPublisher()).publishStreakMilestone(USER_ID, 7, 7, TODAY);
        verify(fixture.cacheInvalidationService()).evictAllForUser(USER_ID);
    }

    @Test
    void rebuildDashboardBreaksStreakWhenActivityGapIsOlderThanYesterday() {
        Fixture fixture = newFixture();
        when(fixture.dashboardSnapshotRepository().findById(USER_ID)).thenReturn(Optional.empty());
        when(fixture.dailyBodyRepository().findTopByUserIdOrderByMetricDateDesc(USER_ID)).thenReturn(Optional.empty());
        when(fixture.dailyNutritionRepository().findByUserIdAndActivityDate(USER_ID, TODAY)).thenReturn(Optional.empty());
        when(fixture.dailyWorkoutRepository()
                .findByUserIdAndActivityDateBetweenOrderByActivityDateDesc(USER_ID, TODAY.minusDays(6), TODAY))
                .thenReturn(List.of(workoutRow(TODAY.minusDays(2), 1, 30)));
        when(fixture.goalProgressRepository().findByUserIdOrderByTargetDateAscGoalIdAsc(USER_ID)).thenReturn(List.of());

        fixture.service().rebuildDashboard(USER_ID);

        ArgumentCaptor<AnalyticsUserDashboardSnapshotEntity> captor =
                ArgumentCaptor.forClass(AnalyticsUserDashboardSnapshotEntity.class);
        verify(fixture.dashboardSnapshotRepository()).save(captor.capture());
        assertThat(captor.getValue().getActiveStreakDays()).isZero();
    }

    @Test
    void rebuildNutritionCreatesProjectionFromMealContributionsAndActiveTargets() {
        Fixture fixture = newFixture();
        when(fixture.dailyNutritionRepository().findByUserIdAndActivityDate(USER_ID, TODAY))
                .thenReturn(Optional.empty());
        when(fixture.mealContributionRepository().findByUserIdAndActivityDate(USER_ID, TODAY))
                .thenReturn(List.of(
                        mealContribution(600, "40.00", "52.00", "14.00"),
                        mealContribution(null, "10.00", null, "8.00")));
        when(fixture.goalProgressRepository().findByUserIdOrderByTargetDateAscGoalIdAsc(USER_ID))
                .thenReturn(List.of(
                        goal("CALORIES", "ACTIVE", "2000.00"),
                        goal("PROTEIN", "active", "100.00"),
                        goal("CALORIES", "PAUSED", "5000.00")));
        stubDashboardRebuild(fixture);

        AnalyticsRebuildResponse response = fixture.service().rebuildNutrition(USER_ID, TODAY);

        ArgumentCaptor<AnalyticsUserDailyNutritionEntity> captor =
                ArgumentCaptor.forClass(AnalyticsUserDailyNutritionEntity.class);
        verify(fixture.dailyNutritionRepository()).save(captor.capture());
        AnalyticsUserDailyNutritionEntity saved = captor.getValue();
        assertThat(response.scope()).isEqualTo("nutrition");
        assertThat(response.date()).isEqualTo(TODAY);
        assertThat(saved.getMealCount()).isEqualTo(2);
        assertThat(saved.getTotalCaloriesConsumed()).isEqualTo(600);
        assertThat(saved.getTotalProteinGrams()).isEqualByComparingTo("50.00");
        assertThat(saved.getTotalCarbsGrams()).isEqualByComparingTo("52.00");
        assertThat(saved.getTotalFatGrams()).isEqualByComparingTo("22.00");
        assertThat(saved.getCalorieTarget()).isEqualTo(2000);
        assertThat(saved.getProteinTargetGrams()).isEqualByComparingTo("100.00");
        assertThat(saved.getCalorieAdherenceRatio()).isEqualByComparingTo("0.3000");
        assertThat(saved.getMacroAdherenceScore()).isEqualByComparingTo("0.5000");
        verify(fixture.cacheInvalidationService(), times(2)).evictAllForUser(USER_ID);
    }

    @Test
    void rebuildWorkoutsCreatesProjectionFromWorkoutContributionsIgnoringNullTotals() {
        Fixture fixture = newFixture();
        when(fixture.dailyWorkoutRepository().findByUserIdAndActivityDate(USER_ID, TODAY))
                .thenReturn(Optional.empty());
        when(fixture.workoutContributionRepository().findByUserIdAndActivityDate(USER_ID, TODAY))
                .thenReturn(List.of(
                        workoutContribution(45, 320, 6),
                        workoutContribution(null, 180, null)));
        stubDashboardRebuild(fixture);

        AnalyticsRebuildResponse response = fixture.service().rebuildWorkouts(USER_ID, TODAY);

        ArgumentCaptor<AnalyticsUserDailyWorkoutEntity> captor =
                ArgumentCaptor.forClass(AnalyticsUserDailyWorkoutEntity.class);
        verify(fixture.dailyWorkoutRepository()).save(captor.capture());
        AnalyticsUserDailyWorkoutEntity saved = captor.getValue();
        assertThat(response.scope()).isEqualTo("workouts");
        assertThat(response.date()).isEqualTo(TODAY);
        assertThat(saved.getCompletedWorkoutCount()).isEqualTo(2);
        assertThat(saved.getTotalWorkoutMinutes()).isEqualTo(45);
        assertThat(saved.getTotalCaloriesBurned()).isEqualTo(500);
        assertThat(saved.getTotalExerciseCount()).isEqualTo(6);
        assertThat(saved.getRolling7DayWorkoutCount()).isZero();
        assertThat(saved.getRolling28DayWorkoutCount()).isZero();
        verify(fixture.cacheInvalidationService(), times(2)).evictAllForUser(USER_ID);
    }

    private Fixture newFixture() {
        AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository =
                mock(AnalyticsUserDashboardSnapshotRepository.class);
        AnalyticsUserDailyBodyRepository dailyBodyRepository = mock(AnalyticsUserDailyBodyRepository.class);
        AnalyticsUserDailyNutritionRepository dailyNutritionRepository =
                mock(AnalyticsUserDailyNutritionRepository.class);
        AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        AnalyticsUserGoalProgressRepository goalProgressRepository = mock(AnalyticsUserGoalProgressRepository.class);
        AnalyticsMealContributionRepository mealContributionRepository = mock(AnalyticsMealContributionRepository.class);
        AnalyticsWorkoutContributionRepository workoutContributionRepository =
                mock(AnalyticsWorkoutContributionRepository.class);
        AnalyticsCacheInvalidationService cacheInvalidationService = mock(AnalyticsCacheInvalidationService.class);
        AnalyticsEventPublisher analyticsEventPublisher = mock(AnalyticsEventPublisher.class);
        AnalyticsRebuildServiceImpl service = new AnalyticsRebuildServiceImpl(
                dashboardSnapshotRepository,
                dailyBodyRepository,
                dailyNutritionRepository,
                dailyWorkoutRepository,
                goalProgressRepository,
                mealContributionRepository,
                workoutContributionRepository,
                cacheInvalidationService,
                analyticsEventPublisher,
                clock);
        return new Fixture(
                service,
                dashboardSnapshotRepository,
                dailyBodyRepository,
                dailyNutritionRepository,
                dailyWorkoutRepository,
                goalProgressRepository,
                mealContributionRepository,
                workoutContributionRepository,
                cacheInvalidationService,
                analyticsEventPublisher);
    }

    private void stubDashboardRebuild(Fixture fixture) {
        when(fixture.dashboardSnapshotRepository().findById(USER_ID))
                .thenReturn(Optional.of(dashboardSnapshot(0)));
        when(fixture.dailyBodyRepository().findTopByUserIdOrderByMetricDateDesc(USER_ID))
                .thenReturn(Optional.empty());
        when(fixture.dailyNutritionRepository().findByUserIdAndActivityDate(eq(USER_ID), any()))
                .thenReturn(Optional.empty());
        when(fixture.dailyWorkoutRepository()
                .findByUserIdAndActivityDateBetweenOrderByActivityDateDesc(eq(USER_ID), any(), any()))
                .thenReturn(List.of());
    }

    private AnalyticsUserDashboardSnapshotEntity dashboardSnapshot(int activeStreakDays) {
        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setUserId(USER_ID);
        snapshot.setTodayWorkoutCount(0);
        snapshot.setWeekWorkoutCount(0);
        snapshot.setWeekWorkoutMinutes(0);
        snapshot.setActiveStreakDays(activeStreakDays);
        snapshot.setGeneratedAt(NOW.minusSeconds(3600));
        return snapshot;
    }

    private AnalyticsUserDailyBodyEntity bodyMetrics() {
        AnalyticsUserDailyBodyEntity body = new AnalyticsUserDailyBodyEntity();
        body.setUserId(USER_ID);
        body.setMetricDate(TODAY);
        body.setWeightKg(new BigDecimal("78.40"));
        body.setBodyFatRate(new BigDecimal("19.80"));
        body.setBmi(new BigDecimal("24.70"));
        body.setWeightDeltaFromStart(new BigDecimal("-1.60"));
        body.setProjectionUpdatedAt(NOW.minusSeconds(120));
        return body;
    }

    private AnalyticsUserDailyNutritionEntity nutritionRow(int calories, String proteinGrams) {
        AnalyticsUserDailyNutritionEntity nutrition = new AnalyticsUserDailyNutritionEntity();
        nutrition.setUserId(USER_ID);
        nutrition.setActivityDate(TODAY);
        nutrition.setTotalCaloriesConsumed(calories);
        nutrition.setCalorieTarget(2200);
        nutrition.setTotalProteinGrams(new BigDecimal(proteinGrams));
        nutrition.setProteinTargetGrams(new BigDecimal("160.00"));
        nutrition.setProjectionUpdatedAt(NOW.minusSeconds(60));
        return nutrition;
    }

    private List<AnalyticsUserDailyWorkoutEntity> consecutiveWorkoutRows(int days) {
        List<AnalyticsUserDailyWorkoutEntity> rows = new ArrayList<>();
        for (int offset = 0; offset < days; offset++) {
            rows.add(workoutRow(TODAY.minusDays(offset), 1, 45));
        }
        return rows;
    }

    private AnalyticsUserDailyWorkoutEntity workoutRow(LocalDate date, int completedCount, int minutes) {
        AnalyticsUserDailyWorkoutEntity workout = new AnalyticsUserDailyWorkoutEntity();
        workout.setUserId(USER_ID);
        workout.setActivityDate(date);
        workout.setCompletedWorkoutCount(completedCount);
        workout.setTotalWorkoutMinutes(minutes);
        workout.setTotalCaloriesBurned(300);
        workout.setTotalExerciseCount(6);
        workout.setRolling7DayWorkoutCount(0);
        workout.setRolling28DayWorkoutCount(0);
        workout.setProjectionUpdatedAt(NOW.minusSeconds(30));
        return workout;
    }

    private AnalyticsMealContributionEntity mealContribution(Integer calories,
                                                             String proteinGrams,
                                                             String carbsGrams,
                                                             String fatGrams) {
        AnalyticsMealContributionEntity contribution = new AnalyticsMealContributionEntity();
        contribution.setMealId(UUID.randomUUID());
        contribution.setUserId(USER_ID);
        contribution.setActivityDate(TODAY);
        contribution.setTotalCalories(calories);
        contribution.setTotalProteinGrams(proteinGrams == null ? null : new BigDecimal(proteinGrams));
        contribution.setTotalCarbsGrams(carbsGrams == null ? null : new BigDecimal(carbsGrams));
        contribution.setTotalFatGrams(fatGrams == null ? null : new BigDecimal(fatGrams));
        contribution.setItemCount(1);
        contribution.setLastAppliedAt(NOW.minusSeconds(45));
        return contribution;
    }

    private AnalyticsWorkoutContributionEntity workoutContribution(Integer minutes, Integer calories, Integer exercises) {
        AnalyticsWorkoutContributionEntity contribution = new AnalyticsWorkoutContributionEntity();
        contribution.setSessionId(UUID.randomUUID());
        contribution.setUserId(USER_ID);
        contribution.setActivityDate(TODAY);
        contribution.setDurationMinutes(minutes);
        contribution.setCaloriesBurned(calories);
        contribution.setExerciseCount(exercises);
        contribution.setLastAppliedAt(NOW.minusSeconds(45));
        return contribution;
    }

    private AnalyticsUserGoalProgressEntity goal(String type, String status, String targetValue) {
        AnalyticsUserGoalProgressEntity goal = new AnalyticsUserGoalProgressEntity();
        goal.setGoalId(UUID.randomUUID());
        goal.setUserId(USER_ID);
        goal.setGoalType(type);
        goal.setGoalStatus(status);
        goal.setTargetValue(new BigDecimal(targetValue));
        goal.setTargetDate(TODAY.plusDays(30));
        goal.setOnTrack(true);
        goal.setLastEvaluatedAt(NOW.minusSeconds(90));
        return goal;
    }

    private record Fixture(
            AnalyticsRebuildServiceImpl service,
            AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository,
            AnalyticsUserDailyBodyRepository dailyBodyRepository,
            AnalyticsUserDailyNutritionRepository dailyNutritionRepository,
            AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository,
            AnalyticsUserGoalProgressRepository goalProgressRepository,
            AnalyticsMealContributionRepository mealContributionRepository,
            AnalyticsWorkoutContributionRepository workoutContributionRepository,
            AnalyticsCacheInvalidationService cacheInvalidationService,
            AnalyticsEventPublisher analyticsEventPublisher) {
    }
}
