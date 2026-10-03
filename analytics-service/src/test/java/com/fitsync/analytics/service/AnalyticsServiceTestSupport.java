package com.fitsync.analytics.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.analytics.config.AnalyticsProperties;
import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyWorkoutEntity;
import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import com.fitsync.analytics.entity.AnalyticsUserGoalProgressEntity;
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import com.fitsync.analytics.repository.AnalyticsUserStreakRollupRepository;
import com.fitsync.analytics.repository.AnalyticsUserWorkoutRecordsRepository;
import com.fitsync.analytics.security.CurrentUserPrincipal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Mock wiring shared by the read-side tests that exercise {@link AnalyticsServiceImpl} in a user zone. */
final class AnalyticsServiceTestSupport {

    static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    final AnalyticsUserDashboardSnapshotRepository dashboardRepository =
            mock(AnalyticsUserDashboardSnapshotRepository.class);
    final AnalyticsUserGoalProgressRepository goalRepository = mock(AnalyticsUserGoalProgressRepository.class);
    final AnalyticsUserDailyBodyRepository bodyRepository = mock(AnalyticsUserDailyBodyRepository.class);
    final AnalyticsUserDailyWorkoutRepository workoutRepository = mock(AnalyticsUserDailyWorkoutRepository.class);
    final AnalyticsUserDailyNutritionRepository nutritionRepository =
            mock(AnalyticsUserDailyNutritionRepository.class);
    final AnalyticsUserStreakRollupRepository streakRollupRepository =
            mock(AnalyticsUserStreakRollupRepository.class);
    AnalyticsUserDashboardSnapshotEntity snapshot;

    AnalyticsServiceTestSupport withZone(String timezone) {
        snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setUserId(USER_ID);
        snapshot.setTimezone(timezone);
        when(dashboardRepository.findById(USER_ID)).thenReturn(Optional.of(snapshot));
        return this;
    }

    AnalyticsServiceImpl service(String instant) {
        return new AnalyticsServiceImpl(
                dashboardRepository, goalRepository, bodyRepository, workoutRepository, nutritionRepository,
                streakRollupRepository, mock(AnalyticsUserWorkoutRecordsRepository.class),
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC),
                new AnalyticsProperties(
                        new AnalyticsProperties.Trends(new AnalyticsProperties.MaxRangeDays(370, 1095, 1830))));
    }

    static CurrentUserPrincipal principal() {
        return new CurrentUserPrincipal(USER_ID, "alice@example.com", "Alice", "USER");
    }

    static AnalyticsUserGoalProgressEntity goal(String type, String target, LocalDate targetDate) {
        AnalyticsUserGoalProgressEntity goal = new AnalyticsUserGoalProgressEntity();
        goal.setGoalId(UUID.randomUUID());
        goal.setUserId(USER_ID);
        goal.setGoalType(type);
        goal.setGoalStatus("ACTIVE");
        goal.setTargetValue(new BigDecimal(target));
        goal.setTargetDate(targetDate);
        // Stored values are yesterday's: they must never reach the response for a daily goal.
        goal.setCurrentValue(new BigDecimal("907"));
        goal.setProgressRatio(new BigDecimal("0.4319"));
        goal.setOnTrack(true);
        goal.setLastEvaluatedAt(Instant.parse("2026-06-13T12:00:00Z"));
        return goal;
    }

    static AnalyticsUserDailyNutritionEntity nutritionDay(String date, int calories, String protein, int meals) {
        AnalyticsUserDailyNutritionEntity row = new AnalyticsUserDailyNutritionEntity();
        row.setUserId(USER_ID);
        row.setActivityDate(LocalDate.parse(date));
        row.setTotalCaloriesConsumed(calories);
        row.setTotalProteinGrams(new BigDecimal(protein));
        row.setTotalCarbsGrams(new BigDecimal("100.00"));
        row.setTotalFatGrams(new BigDecimal("40.00"));
        row.setMealCount(meals);
        return row;
    }

    static AnalyticsUserDailyWorkoutEntity workoutDay(String date, int count, int minutes) {
        AnalyticsUserDailyWorkoutEntity row = new AnalyticsUserDailyWorkoutEntity();
        row.setUserId(USER_ID);
        row.setActivityDate(LocalDate.parse(date));
        row.setCompletedWorkoutCount(count);
        row.setTotalWorkoutMinutes(minutes);
        row.setTotalCaloriesBurned(0);
        row.setTotalExerciseCount(0);
        return row;
    }
}
