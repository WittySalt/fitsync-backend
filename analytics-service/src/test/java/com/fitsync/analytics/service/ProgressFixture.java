package com.fitsync.analytics.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import com.fitsync.analytics.entity.AnalyticsMealContributionEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyBodyEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyWorkoutEntity;
import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import com.fitsync.analytics.entity.AnalyticsUserEnergyProfileEntity;
import com.fitsync.analytics.entity.AnalyticsUserGoalProgressEntity;
import com.fitsync.analytics.repository.AnalyticsMealContributionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserEnergyProfileRepository;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;

/** In-memory rows behind mocked repositories for the Progress services, filtered like the real queries. */
final class ProgressFixture {

    final UUID userId = UUID.randomUUID();
    final AnalyticsUserDashboardSnapshotRepository snapshotRepository = mock(AnalyticsUserDashboardSnapshotRepository.class);
    final AnalyticsUserDailyNutritionRepository nutritionRepository = mock(AnalyticsUserDailyNutritionRepository.class);
    final AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
    final AnalyticsUserDailyBodyRepository bodyRepository = mock(AnalyticsUserDailyBodyRepository.class);
    final AnalyticsMealContributionRepository mealRepository = mock(AnalyticsMealContributionRepository.class);
    final AnalyticsUserEnergyProfileRepository energyRepository = mock(AnalyticsUserEnergyProfileRepository.class);
    final AnalyticsUserGoalProgressRepository goalRepository = mock(AnalyticsUserGoalProgressRepository.class);

    final List<AnalyticsUserDailyNutritionEntity> nutritionRows = new ArrayList<>();
    final List<AnalyticsUserDailyWorkoutEntity> workoutRows = new ArrayList<>();
    final List<AnalyticsUserDailyBodyEntity> bodyRows = new ArrayList<>();
    final List<AnalyticsMealContributionEntity> mealRows = new ArrayList<>();
    final List<AnalyticsUserEnergyProfileEntity> profiles = new ArrayList<>();
    final List<AnalyticsUserGoalProgressEntity> goals = new ArrayList<>();

    AnalyticsEnergyService energy(Clock clock) {
        return new AnalyticsEnergyService(snapshotRepository, nutritionRepository, workoutRepository, bodyRepository,
                mealRepository, energyRepository, clock);
    }

    ProgressGoalService goalService(Clock clock) {
        return new ProgressGoalService(bodyRepository, nutritionRepository, workoutRepository, goalRepository,
                energy(clock), clock);
    }

    ProgressFoodService foodService(Clock clock) {
        return new ProgressFoodService(nutritionRepository, mealRepository, goalRepository, energy(clock), clock);
    }

    static Clock clockAt(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    void storedZone(String zone) {
        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setTimezone(zone);
        when(snapshotRepository.findById(userId)).thenReturn(Optional.of(snapshot));
    }

    AnalyticsUserDailyNutritionEntity day(LocalDate date, int kcal, int meals, String protein) {
        AnalyticsUserDailyNutritionEntity row = new AnalyticsUserDailyNutritionEntity();
        row.setUserId(userId);
        row.setActivityDate(date);
        row.setMealCount(meals);
        row.setTotalCaloriesConsumed(kcal);
        row.setTotalProteinGrams(new BigDecimal(protein));
        row.setTotalCarbsGrams(BigDecimal.ZERO);
        row.setTotalFatGrams(BigDecimal.ZERO);
        nutritionRows.add(row);
        return row;
    }

    void workout(LocalDate date, int sessions, int kcal) {
        AnalyticsUserDailyWorkoutEntity row = new AnalyticsUserDailyWorkoutEntity();
        row.setUserId(userId);
        row.setActivityDate(date);
        row.setCompletedWorkoutCount(sessions);
        row.setTotalWorkoutMinutes(45 * sessions);
        row.setTotalCaloriesBurned(kcal);
        row.setEstimatedCaloriesBurned(0);
        row.setTotalExerciseCount(0);
        row.setRolling7DayWorkoutCount(0);
        row.setRolling28DayWorkoutCount(0);
        workoutRows.add(row);
    }

    AnalyticsUserDailyBodyEntity body(LocalDate date, String kg) {
        AnalyticsUserDailyBodyEntity row = new AnalyticsUserDailyBodyEntity();
        row.setUserId(userId);
        row.setMetricDate(date);
        row.setWeightKg(kg == null ? null : new BigDecimal(kg));
        bodyRows.add(row);
        return row;
    }

    void meal(LocalDate date, String type, int kcal) {
        AnalyticsMealContributionEntity row = new AnalyticsMealContributionEntity();
        row.setMealId(UUID.randomUUID());
        row.setUserId(userId);
        row.setActivityDate(date);
        row.setMealType(type);
        row.setTotalCalories(kcal);
        mealRows.add(row);
    }

    void profile(String computedAt, int bmr) {
        AnalyticsUserEnergyProfileEntity row = new AnalyticsUserEnergyProfileEntity();
        row.setEventId(UUID.randomUUID());
        row.setUserId(userId);
        row.setBmrKcal(bmr);
        row.setComputedAt(Instant.parse(computedAt));
        profiles.add(row);
    }

    AnalyticsUserGoalProgressEntity goal(String type, String target, LocalDate byDate, String evaluatedAt) {
        AnalyticsUserGoalProgressEntity row = new AnalyticsUserGoalProgressEntity();
        row.setGoalId(UUID.randomUUID());
        row.setUserId(userId);
        row.setGoalType(type);
        row.setGoalStatus("ACTIVE");
        row.setTargetValue(new BigDecimal(target));
        row.setTargetDate(byDate);
        row.setLastEvaluatedAt(Instant.parse(evaluatedAt));
        goals.add(row);
        return row;
    }

    void wire() {
        when(nutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(eq(userId), any(), any()))
                .thenAnswer(call -> inRange(nutritionRows, call.getArgument(1), call.getArgument(2),
                        AnalyticsUserDailyNutritionEntity::getActivityDate));
        when(workoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(eq(userId), any(), any()))
                .thenAnswer(call -> inRange(workoutRows, call.getArgument(1), call.getArgument(2),
                        AnalyticsUserDailyWorkoutEntity::getActivityDate));
        when(bodyRepository.findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(eq(userId), any(), any()))
                .thenAnswer(call -> inRange(bodyRows, call.getArgument(1), call.getArgument(2),
                        AnalyticsUserDailyBodyEntity::getMetricDate));
        when(bodyRepository.findTopByUserIdAndWeightKgIsNotNullAndMetricDateLessThanEqualOrderByMetricDateDesc(
                eq(userId), any()))
                .thenAnswer(call -> bodyRows.stream()
                        .filter(row -> row.getWeightKg() != null
                                && !row.getMetricDate().isAfter(call.<LocalDate>getArgument(1)))
                        .max(Comparator.comparing(AnalyticsUserDailyBodyEntity::getMetricDate)));
        when(mealRepository.findByUserIdAndActivityDateBetween(eq(userId), any(), any()))
                .thenAnswer(call -> inRange(mealRows, call.getArgument(1), call.getArgument(2),
                        AnalyticsMealContributionEntity::getActivityDate));
        when(goalRepository.findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(userId, "ACTIVE"))
                .thenAnswer(call -> List.copyOf(goals));
        List<AnalyticsUserEnergyProfileEntity> ordered = profiles.stream()
                .sorted(Comparator.comparing(AnalyticsUserEnergyProfileEntity::getComputedAt)).toList();
        when(energyRepository.findFirstByUserIdAndComputedAtBeforeOrderByComputedAtDesc(eq(userId), any()))
                .thenAnswer(call -> ordered.stream()
                        .filter(row -> row.getComputedAt().isBefore(call.<Instant>getArgument(1)))
                        .reduce((a, b) -> b));
        when(energyRepository.findByUserIdAndComputedAtGreaterThanEqualAndComputedAtBeforeOrderByComputedAtAsc(
                eq(userId), any(), any()))
                .thenAnswer(call -> ordered.stream()
                        .filter(row -> !row.getComputedAt().isBefore(call.<Instant>getArgument(1))
                                && row.getComputedAt().isBefore(call.<Instant>getArgument(2)))
                        .toList());
        when(energyRepository.findFirstByUserIdOrderByComputedAtAsc(userId)).thenReturn(ordered.stream().findFirst());
    }

    private static <T> List<T> inRange(List<T> rows, LocalDate from, LocalDate to, Function<T, LocalDate> dateOf) {
        return rows.stream()
                .filter(row -> !dateOf.apply(row).isBefore(from) && !dateOf.apply(row).isAfter(to))
                .sorted(Comparator.comparing(dateOf))
                .toList();
    }
}
