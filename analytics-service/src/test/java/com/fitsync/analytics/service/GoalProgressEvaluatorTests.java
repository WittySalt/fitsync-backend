package com.fitsync.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyWorkoutEntity;
import com.fitsync.analytics.entity.AnalyticsUserGoalProgressEntity;
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GoalProgressEvaluatorTests {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDate TODAY = LocalDate.parse("2026-06-18");

    private AnalyticsUserGoalProgressRepository goalProgressRepository;
    private AnalyticsUserDailyNutritionRepository dailyNutritionRepository;
    private AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository;
    private AnalyticsUserDailyBodyRepository dailyBodyRepository;
    private AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository;
    private GoalProgressEvaluator evaluator;

    @BeforeEach
    void setUp() {
        goalProgressRepository = mock(AnalyticsUserGoalProgressRepository.class);
        dailyNutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
        dailyWorkoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
        dailyBodyRepository = mock(AnalyticsUserDailyBodyRepository.class);
        dashboardSnapshotRepository = mock(AnalyticsUserDashboardSnapshotRepository.class);
        evaluator = new GoalProgressEvaluator(
                goalProgressRepository,
                dailyNutritionRepository,
                dailyWorkoutRepository,
                dailyBodyRepository,
                dashboardSnapshotRepository,
                Clock.fixed(Instant.parse("2026-06-18T04:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void proteinGoalTracksDailyFloor() {
        AnalyticsUserGoalProgressEntity goal = goal(GoalProgressEvaluator.PROTEIN, "120.00");
        AnalyticsUserDailyNutritionEntity nutrition = nutrition(1800, "130.00", "100.00", "45.00");
        whenActiveGoals(Set.of(GoalProgressEvaluator.PROTEIN), goal);
        when(dailyNutritionRepository.findByUserIdAndActivityDate(USER_ID, TODAY)).thenReturn(Optional.of(nutrition));

        evaluator.recompute(USER_ID, Set.of(GoalProgressEvaluator.PROTEIN), TODAY);

        AnalyticsUserGoalProgressEntity saved = savedGoal();
        assertThat(saved.getCurrentValue()).isEqualByComparingTo("130.00");
        assertThat(saved.getProgressRatio()).isEqualByComparingTo("1.0833");
        assertThat(saved.isOnTrack()).isTrue();
    }

    @Test
    void proteinGoalIsOffTrackWhenUnderFloor() {
        AnalyticsUserGoalProgressEntity goal = goal(GoalProgressEvaluator.PROTEIN, "120.00");
        AnalyticsUserDailyNutritionEntity nutrition = nutrition(1800, "90.00", "100.00", "45.00");
        whenActiveGoals(Set.of(GoalProgressEvaluator.PROTEIN), goal);
        when(dailyNutritionRepository.findByUserIdAndActivityDate(USER_ID, TODAY)).thenReturn(Optional.of(nutrition));

        evaluator.recompute(USER_ID, Set.of(GoalProgressEvaluator.PROTEIN), TODAY);

        AnalyticsUserGoalProgressEntity saved = savedGoal();
        assertThat(saved.getProgressRatio()).isEqualByComparingTo("0.7500");
        assertThat(saved.isOnTrack()).isFalse();
    }

    @Test
    void caloriesGoalTracksBudgetUsedAndFlipsOnTrackWhenOverBudget() {
        AnalyticsUserGoalProgressEntity underBudget = goal(GoalProgressEvaluator.CALORIES, "2000.00");
        AnalyticsUserDailyNutritionEntity underNutrition = nutrition(1800, "90.00", "100.00", "45.00");
        whenActiveGoals(Set.of(GoalProgressEvaluator.CALORIES), underBudget);
        when(dailyNutritionRepository.findByUserIdAndActivityDate(USER_ID, TODAY))
                .thenReturn(Optional.of(underNutrition));

        evaluator.recompute(USER_ID, Set.of(GoalProgressEvaluator.CALORIES), TODAY);

        AnalyticsUserGoalProgressEntity savedUnderBudget = savedGoal();
        assertThat(savedUnderBudget.getCurrentValue()).isEqualByComparingTo("1800");
        assertThat(savedUnderBudget.getProgressRatio()).isEqualByComparingTo("0.9000");
        assertThat(savedUnderBudget.isOnTrack()).isTrue();

        AnalyticsUserGoalProgressEntity overBudget = goal(GoalProgressEvaluator.CALORIES, "2000.00");
        AnalyticsUserDailyNutritionEntity overNutrition = nutrition(2200, "90.00", "100.00", "45.00");
        whenActiveGoals(Set.of(GoalProgressEvaluator.CALORIES), overBudget);
        when(dailyNutritionRepository.findByUserIdAndActivityDate(USER_ID, TODAY)).thenReturn(Optional.of(overNutrition));

        evaluator.recompute(USER_ID, Set.of(GoalProgressEvaluator.CALORIES), TODAY);

        AnalyticsUserGoalProgressEntity savedOverBudget = savedGoals().get(1);
        assertThat(savedOverBudget.getCurrentValue()).isEqualByComparingTo("2200");
        assertThat(savedOverBudget.getProgressRatio()).isEqualByComparingTo("1.1000");
        assertThat(savedOverBudget.isOnTrack()).isFalse();
    }

    @Test
    void carbsAndFatUseDailyCeilingSemantics() {
        AnalyticsUserGoalProgressEntity carbs = goal(GoalProgressEvaluator.CARBS, "180.00");
        AnalyticsUserGoalProgressEntity fat = goal(GoalProgressEvaluator.FAT, "70.00");
        AnalyticsUserDailyNutritionEntity nutrition = nutrition(1800, "90.00", "200.00", "50.00");
        whenActiveGoals(Set.of(GoalProgressEvaluator.CARBS, GoalProgressEvaluator.FAT), carbs, fat);
        when(dailyNutritionRepository.findByUserIdAndActivityDate(USER_ID, TODAY)).thenReturn(Optional.of(nutrition));

        evaluator.recompute(USER_ID, Set.of(GoalProgressEvaluator.CARBS, GoalProgressEvaluator.FAT), TODAY);

        List<AnalyticsUserGoalProgressEntity> saved = savedGoals();
        assertThat(saved.get(0).getProgressRatio()).isEqualByComparingTo("1.1111");
        assertThat(saved.get(0).isOnTrack()).isFalse();
        assertThat(saved.get(1).getProgressRatio()).isEqualByComparingTo("0.7143");
        assertThat(saved.get(1).isOnTrack()).isTrue();
    }

    @Test
    void workoutFrequencyUsesRollingSevenDayCountAndPace() {
        AnalyticsUserGoalProgressEntity goal = goal(GoalProgressEvaluator.WORKOUT_FREQUENCY, "4.00");
        whenActiveGoals(Set.of(GoalProgressEvaluator.WORKOUT_FREQUENCY), goal);
        when(dailyWorkoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                USER_ID, TODAY.minusDays(6), TODAY))
                .thenReturn(List.of(workout(TODAY.minusDays(2), 1), workout(TODAY, 2)));

        evaluator.recompute(USER_ID, Set.of(GoalProgressEvaluator.WORKOUT_FREQUENCY), TODAY);

        AnalyticsUserGoalProgressEntity saved = savedGoal();
        assertThat(saved.getCurrentValue()).isEqualByComparingTo("3");
        assertThat(saved.getProgressRatio()).isEqualByComparingTo("0.7500");
        assertThat(saved.isOnTrack()).isTrue();
    }

    @Test
    void workoutFrequencyIsOffTrackWhenUnderPace() {
        AnalyticsUserGoalProgressEntity goal = goal(GoalProgressEvaluator.WORKOUT_FREQUENCY, "4.00");
        whenActiveGoals(Set.of(GoalProgressEvaluator.WORKOUT_FREQUENCY), goal);
        when(dailyWorkoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(
                USER_ID, TODAY.minusDays(6), TODAY))
                .thenReturn(List.of(workout(TODAY, 1)));

        evaluator.recompute(USER_ID, Set.of(GoalProgressEvaluator.WORKOUT_FREQUENCY), TODAY);

        AnalyticsUserGoalProgressEntity saved = savedGoal();
        assertThat(saved.getProgressRatio()).isEqualByComparingTo("0.2500");
        assertThat(saved.isOnTrack()).isFalse();
    }

    @Test
    void nullOrZeroTargetClearsRatioAndMarksOffTrack() {
        AnalyticsUserGoalProgressEntity nullTarget = goal(GoalProgressEvaluator.CALORIES, null);
        AnalyticsUserGoalProgressEntity zeroTarget = goal(GoalProgressEvaluator.PROTEIN, "0");
        AnalyticsUserDailyNutritionEntity nutrition = nutrition(1800, "90.00", "100.00", "45.00");
        whenActiveGoals(Set.of(GoalProgressEvaluator.CALORIES, GoalProgressEvaluator.PROTEIN), nullTarget, zeroTarget);
        when(dailyNutritionRepository.findByUserIdAndActivityDate(USER_ID, TODAY)).thenReturn(Optional.of(nutrition));

        evaluator.recompute(USER_ID, Set.of(GoalProgressEvaluator.CALORIES, GoalProgressEvaluator.PROTEIN), TODAY);

        assertThat(savedGoals())
                .allSatisfy(goal -> {
                    assertThat(goal.getProgressRatio()).isNull();
                    assertThat(goal.isOnTrack()).isFalse();
                });
    }

    private void whenActiveGoals(Set<String> goalTypes, AnalyticsUserGoalProgressEntity... goals) {
        when(goalProgressRepository.findByUserIdAndGoalStatusAndGoalTypeInOrderByTargetDateAscGoalIdAsc(
                USER_ID, GoalExpiryPolicy.ACTIVE_STATUS, goalTypes))
                .thenReturn(List.of(goals));
    }

    private AnalyticsUserGoalProgressEntity savedGoal() {
        return savedGoals().get(0);
    }

    private List<AnalyticsUserGoalProgressEntity> savedGoals() {
        ArgumentCaptor<AnalyticsUserGoalProgressEntity> captor =
                ArgumentCaptor.forClass(AnalyticsUserGoalProgressEntity.class);
        org.mockito.Mockito.verify(goalProgressRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private AnalyticsUserGoalProgressEntity goal(String goalType, String targetValue) {
        AnalyticsUserGoalProgressEntity goal = new AnalyticsUserGoalProgressEntity();
        goal.setGoalId(UUID.randomUUID());
        goal.setUserId(USER_ID);
        goal.setGoalType(goalType);
        goal.setGoalStatus(GoalExpiryPolicy.ACTIVE_STATUS);
        goal.setTargetValue(targetValue == null ? null : new BigDecimal(targetValue));
        goal.setTargetDate(TODAY.plusDays(30));
        goal.setLastEvaluatedAt(Instant.parse("2026-06-17T04:00:00Z"));
        return goal;
    }

    private AnalyticsUserDailyNutritionEntity nutrition(int calories, String protein, String carbs, String fat) {
        AnalyticsUserDailyNutritionEntity nutrition = new AnalyticsUserDailyNutritionEntity();
        nutrition.setUserId(USER_ID);
        nutrition.setActivityDate(TODAY);
        nutrition.setTotalCaloriesConsumed(calories);
        nutrition.setTotalProteinGrams(new BigDecimal(protein));
        nutrition.setTotalCarbsGrams(new BigDecimal(carbs));
        nutrition.setTotalFatGrams(new BigDecimal(fat));
        return nutrition;
    }

    private AnalyticsUserDailyWorkoutEntity workout(LocalDate activityDate, int completedCount) {
        AnalyticsUserDailyWorkoutEntity workout = new AnalyticsUserDailyWorkoutEntity();
        workout.setUserId(USER_ID);
        workout.setActivityDate(activityDate);
        workout.setCompletedWorkoutCount(completedCount);
        return workout;
    }
}
