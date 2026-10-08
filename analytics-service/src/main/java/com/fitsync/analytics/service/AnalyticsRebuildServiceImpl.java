package com.fitsync.analytics.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.fitsync.analytics.dto.AnalyticsRebuildResponse;
import com.fitsync.analytics.entity.AnalyticsMealContributionEntity;
import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyWorkoutEntity;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recomputes read models from their source rows instead of applying event deltas: one day's nutrition or
 * workout row from the stored meal or workout contributions, and the dashboard snapshot from the daily rows
 * and the active goals. Used by the admin rebuild endpoints and by the nightly rollup. A rebuild of the
 * dashboard also announces any streak milestone the new streak crossed.
 */
@Service
public class AnalyticsRebuildServiceImpl implements AnalyticsRebuildService {

    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String CALORIES_GOAL_TYPE = "CALORIES";
    private static final String PROTEIN_GOAL_TYPE = "PROTEIN";
    private static final List<Integer> STREAK_MILESTONES = List.of(7, 14, 30, 60, 100, 180, 365);

    private final AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository;
    private final AnalyticsUserDailyBodyRepository dailyBodyRepository;
    private final AnalyticsUserDailyNutritionRepository dailyNutritionRepository;
    private final AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository;
    private final AnalyticsUserGoalProgressRepository goalProgressRepository;
    private final AnalyticsMealContributionRepository mealContributionRepository;
    private final AnalyticsWorkoutContributionRepository workoutContributionRepository;
    private final AnalyticsCacheInvalidationService cacheInvalidationService;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final Clock clock;

    public AnalyticsRebuildServiceImpl(AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository,
                                       AnalyticsUserDailyBodyRepository dailyBodyRepository,
                                       AnalyticsUserDailyNutritionRepository dailyNutritionRepository,
                                       AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository,
                                       AnalyticsUserGoalProgressRepository goalProgressRepository,
                                       AnalyticsMealContributionRepository mealContributionRepository,
                                       AnalyticsWorkoutContributionRepository workoutContributionRepository,
                                       AnalyticsCacheInvalidationService cacheInvalidationService,
                                       AnalyticsEventPublisher analyticsEventPublisher,
                                       Clock clock) {
        this.dashboardSnapshotRepository = dashboardSnapshotRepository;
        this.dailyBodyRepository = dailyBodyRepository;
        this.dailyNutritionRepository = dailyNutritionRepository;
        this.dailyWorkoutRepository = dailyWorkoutRepository;
        this.goalProgressRepository = goalProgressRepository;
        this.mealContributionRepository = mealContributionRepository;
        this.workoutContributionRepository = workoutContributionRepository;
        this.cacheInvalidationService = cacheInvalidationService;
        this.analyticsEventPublisher = analyticsEventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AnalyticsRebuildResponse rebuildDashboard(UUID userId) {
        AnalyticsUserDashboardSnapshotEntity snapshot = dashboardSnapshotRepository.findById(userId)
                .orElseGet(() -> createDashboardSnapshot(userId));
        ZoneId userZone = UserZone.of(snapshot.getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(userZone));

        dailyBodyRepository.findTopByUserIdOrderByMetricDateDesc(userId)
                .ifPresent(body -> {
                    snapshot.setLatestWeightKg(body.getWeightKg());
                    snapshot.setLatestBodyFatRate(body.getBodyFatRate());
                    snapshot.setBmi(body.getBmi());
                    snapshot.setWeightChange30d(BodyWeightChange.over30Days(
                            dailyBodyRepository, userId, body.getMetricDate(), body.getWeightKg()));
                });

        dailyBodyRepository.findTopByUserIdAndWaistCmIsNotNullOrderByMetricDateDesc(userId)
                .ifPresent(body -> {
                    snapshot.setLatestWaistCm(body.getWaistCm());
                    snapshot.setWaistMeasuredOn(body.getMetricDate());
                });

        // Without a row for today nothing was eaten today, so the stored figures go back to zero rather
        // than keep the last day that had a meal: the rebuild moves generatedAt to now, which would
        // otherwise date that day's totals as today's. The targets are not taken from the row; the
        // active goals set them below.
        AnalyticsUserDailyNutritionEntity todayNutrition =
                dailyNutritionRepository.findByUserIdAndActivityDate(userId, today).orElse(null);
        snapshot.setTodayCaloriesConsumed(
                todayNutrition == null ? Integer.valueOf(0) : todayNutrition.getTotalCaloriesConsumed());
        snapshot.setTodayProteinGrams(todayNutrition == null ? BigDecimal.ZERO : todayNutrition.getTotalProteinGrams());

        List<AnalyticsUserDailyWorkoutEntity> lastSeven = dailyWorkoutRepository
                .findByUserIdAndActivityDateBetweenOrderByActivityDateDesc(userId, today.minusDays(6), today);
        snapshot.setTodayWorkoutCount(lastSeven.stream()
                .filter(row -> today.equals(row.getActivityDate()))
                .map(AnalyticsUserDailyWorkoutEntity::getCompletedWorkoutCount)
                .findFirst()
                .orElse(0));
        snapshot.setWeekWorkoutCount(lastSeven.stream().mapToInt(row -> defaultInt(row.getCompletedWorkoutCount())).sum());
        snapshot.setWeekWorkoutMinutes(lastSeven.stream().mapToInt(row -> defaultInt(row.getTotalWorkoutMinutes())).sum());
        int previousStreak = defaultInt(snapshot.getActiveStreakDays());
        int newStreak = ActiveStreak.days(dailyWorkoutRepository
                .findByUserIdAndActivityDateBetweenOrderByActivityDateDesc(
                        userId, today.minusDays(ActiveStreak.WINDOW_DAYS), today), today);
        snapshot.setActiveStreakDays(newStreak);

        applyTargetsFromGoals(snapshot, userId);
        snapshot.setGeneratedAt(Instant.now(clock));
        dashboardSnapshotRepository.save(snapshot);
        emitStreakMilestones(userId, previousStreak, newStreak, today);
        cacheInvalidationService.evictForUser(userId);
        return new AnalyticsRebuildResponse(userId, "dashboard", null, snapshot.getGeneratedAt());
    }

    /**
     * Emits a StreakMilestoneReached event for each configured milestone the streak crossed upward in
     * this rebuild (previous &lt; milestone &le; new). Emission is in the same transaction as the
     * snapshot save; the publisher de-duplicates via a deterministic event id so a milestone is never
     * announced twice.
     */
    private void emitStreakMilestones(UUID userId, int previousStreak, int newStreak, LocalDate today) {
        if (newStreak <= previousStreak) {
            return;
        }
        for (Integer milestone : STREAK_MILESTONES) {
            if (previousStreak < milestone && newStreak >= milestone) {
                analyticsEventPublisher.publishStreakMilestone(userId, newStreak, milestone, today);
            }
        }
    }

    @Override
    @Transactional
    public AnalyticsRebuildResponse rebuildNutrition(UUID userId, LocalDate date) {
        AnalyticsUserDailyNutritionEntity daily = dailyNutritionRepository.findByUserIdAndActivityDate(userId, date)
                .orElseGet(() -> createNutritionRow(userId, date));
        List<AnalyticsMealContributionEntity> contributions = mealContributionRepository.findByUserIdAndActivityDate(userId, date);
        daily.setMealCount(contributions.size());
        daily.setTotalCaloriesConsumed(contributions.stream()
                .map(AnalyticsMealContributionEntity::getTotalCalories)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum());
        daily.setTotalProteinGrams(contributions.stream()
                .map(AnalyticsMealContributionEntity::getTotalProteinGrams)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        daily.setTotalCarbsGrams(contributions.stream()
                .map(AnalyticsMealContributionEntity::getTotalCarbsGrams)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        daily.setTotalFatGrams(contributions.stream()
                .map(AnalyticsMealContributionEntity::getTotalFatGrams)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        applyTargetsFromGoals(daily, userId);
        daily.setProjectionUpdatedAt(Instant.now(clock));
        NutritionAdherence.recalculate(daily);
        dailyNutritionRepository.save(daily);
        rebuildDashboard(userId);
        cacheInvalidationService.evictForUser(userId);
        return new AnalyticsRebuildResponse(userId, "nutrition", date, Instant.now(clock));
    }

    @Override
    @Transactional
    public AnalyticsRebuildResponse rebuildWorkouts(UUID userId, LocalDate date) {
        AnalyticsUserDailyWorkoutEntity daily = dailyWorkoutRepository.findByUserIdAndActivityDate(userId, date)
                .orElseGet(() -> createWorkoutRow(userId, date));
        List<AnalyticsWorkoutContributionEntity> contributions = workoutContributionRepository.findByUserIdAndActivityDate(userId, date);
        daily.setCompletedWorkoutCount(contributions.size());
        daily.setTotalWorkoutMinutes(contributions.stream()
                .map(AnalyticsWorkoutContributionEntity::getDurationMinutes)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum());
        daily.setTotalCaloriesBurned(contributions.stream()
                .map(AnalyticsWorkoutContributionEntity::getCaloriesBurned)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum());
        daily.setEstimatedCaloriesBurned(contributions.stream()
                .filter(AnalyticsWorkoutContributionEntity::isCaloriesEstimated)
                .map(AnalyticsWorkoutContributionEntity::getCaloriesBurned)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum());
        daily.setTotalExerciseCount(contributions.stream()
                .map(AnalyticsWorkoutContributionEntity::getExerciseCount)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum());
        daily.setProjectionUpdatedAt(Instant.now(clock));
        dailyWorkoutRepository.save(daily);
        rebuildDashboard(userId);
        cacheInvalidationService.evictForUser(userId);
        return new AnalyticsRebuildResponse(userId, "workouts", date, Instant.now(clock));
    }

    private AnalyticsUserDashboardSnapshotEntity createDashboardSnapshot(UUID userId) {
        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setUserId(userId);
        snapshot.setTodayWorkoutCount(0);
        snapshot.setWeekWorkoutCount(0);
        snapshot.setWeekWorkoutMinutes(0);
        snapshot.setActiveStreakDays(0);
        snapshot.setGeneratedAt(Instant.now(clock));
        return snapshot;
    }

    private AnalyticsUserDailyNutritionEntity createNutritionRow(UUID userId, LocalDate date) {
        AnalyticsUserDailyNutritionEntity daily = new AnalyticsUserDailyNutritionEntity();
        daily.setUserId(userId);
        daily.setActivityDate(date);
        daily.setMealCount(0);
        daily.setTotalCaloriesConsumed(0);
        daily.setTotalProteinGrams(BigDecimal.ZERO);
        daily.setTotalCarbsGrams(BigDecimal.ZERO);
        daily.setTotalFatGrams(BigDecimal.ZERO);
        daily.setProjectionUpdatedAt(Instant.now(clock));
        return daily;
    }

    private AnalyticsUserDailyWorkoutEntity createWorkoutRow(UUID userId, LocalDate date) {
        AnalyticsUserDailyWorkoutEntity daily = new AnalyticsUserDailyWorkoutEntity();
        daily.setUserId(userId);
        daily.setActivityDate(date);
        daily.setCompletedWorkoutCount(0);
        daily.setTotalWorkoutMinutes(0);
        daily.setTotalCaloriesBurned(0);
        daily.setEstimatedCaloriesBurned(0);
        daily.setTotalExerciseCount(0);
        daily.setRolling7DayWorkoutCount(0);
        daily.setRolling28DayWorkoutCount(0);
        daily.setProjectionUpdatedAt(Instant.now(clock));
        return daily;
    }

    /** Active calorie and protein goals set the targets; with several, the last in target-date order wins. */
    private void applyTargetsFromGoals(AnalyticsUserDashboardSnapshotEntity snapshot, UUID userId) {
        snapshot.setTodayCaloriesTarget(null);
        snapshot.setTodayProteinTargetGrams(null);
        List<AnalyticsUserGoalProgressEntity> goals = goalProgressRepository.findByUserIdOrderByTargetDateAscGoalIdAsc(userId);
        goals.stream()
                .filter(goal -> ACTIVE_STATUS.equals(normalize(goal.getGoalStatus())))
                .forEach(goal -> {
                    String goalType = normalize(goal.getGoalType());
                    if (CALORIES_GOAL_TYPE.equals(goalType) && goal.getTargetValue() != null) {
                        snapshot.setTodayCaloriesTarget(goal.getTargetValue().intValue());
                    } else if (PROTEIN_GOAL_TYPE.equals(goalType)) {
                        snapshot.setTodayProteinTargetGrams(goal.getTargetValue());
                    }
                });
    }

    private void applyTargetsFromGoals(AnalyticsUserDailyNutritionEntity daily, UUID userId) {
        daily.setCalorieTarget(null);
        daily.setProteinTargetGrams(null);
        List<AnalyticsUserGoalProgressEntity> goals = goalProgressRepository.findByUserIdOrderByTargetDateAscGoalIdAsc(userId);
        goals.stream()
                .filter(goal -> ACTIVE_STATUS.equals(normalize(goal.getGoalStatus())))
                .forEach(goal -> {
                    String goalType = normalize(goal.getGoalType());
                    if (CALORIES_GOAL_TYPE.equals(goalType) && goal.getTargetValue() != null) {
                        daily.setCalorieTarget(goal.getTargetValue().intValue());
                    } else if (PROTEIN_GOAL_TYPE.equals(goalType)) {
                        daily.setProteinTargetGrams(goal.getTargetValue());
                    }
                });
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }
}
