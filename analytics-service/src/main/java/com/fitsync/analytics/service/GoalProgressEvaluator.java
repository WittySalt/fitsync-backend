package com.fitsync.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fitsync.analytics.entity.AnalyticsUserDailyBodyEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;
import com.fitsync.analytics.entity.AnalyticsUserGoalProgressEntity;
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalProgressEvaluator {

    public static final String CALORIES = "CALORIES";
    public static final String PROTEIN = "PROTEIN";
    public static final String CARBS = "CARBS";
    public static final String FAT = "FAT";
    public static final String WEIGHT = "WEIGHT";
    public static final String WORKOUT_FREQUENCY = "WORKOUT_FREQUENCY";

    public static final Set<String> NUTRITION_GOAL_TYPES = Set.of(CALORIES, PROTEIN, CARBS, FAT);
    public static final Set<String> ALL_GOAL_TYPES = Set.of(
            CALORIES, PROTEIN, CARBS, FAT, WEIGHT, WORKOUT_FREQUENCY);

    private static final ZoneId DEFAULT_ZONE = ZoneOffset.UTC;
    private static final BigDecimal SEVEN_DAYS = BigDecimal.valueOf(7);

    private final AnalyticsUserGoalProgressRepository goalProgressRepository;
    private final AnalyticsUserDailyNutritionRepository dailyNutritionRepository;
    private final AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository;
    private final AnalyticsUserDailyBodyRepository dailyBodyRepository;
    private final AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository;
    private final Clock clock;

    public GoalProgressEvaluator(AnalyticsUserGoalProgressRepository goalProgressRepository,
                                 AnalyticsUserDailyNutritionRepository dailyNutritionRepository,
                                 AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository,
                                 AnalyticsUserDailyBodyRepository dailyBodyRepository,
                                 AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository,
                                 Clock clock) {
        this.goalProgressRepository = goalProgressRepository;
        this.dailyNutritionRepository = dailyNutritionRepository;
        this.dailyWorkoutRepository = dailyWorkoutRepository;
        this.dailyBodyRepository = dailyBodyRepository;
        this.dashboardSnapshotRepository = dashboardSnapshotRepository;
        this.clock = clock;
    }

    @Transactional
    public void recompute(UUID userId, Set<String> goalTypes, LocalDate todayInUserTz) {
        if (userId == null || goalTypes == null || goalTypes.isEmpty() || todayInUserTz == null) {
            return;
        }

        Set<String> normalizedGoalTypes = normalizeGoalTypes(goalTypes);
        if (normalizedGoalTypes.isEmpty()) {
            return;
        }

        Instant evaluatedAt = Instant.now(clock);
        goalProgressRepository.findByUserIdAndGoalStatusAndGoalTypeInOrderByTargetDateAscGoalIdAsc(
                        userId, GoalExpiryPolicy.ACTIVE_STATUS, normalizedGoalTypes)
                .forEach(goal -> {
                    evaluate(goal, todayInUserTz, evaluatedAt);
                    goalProgressRepository.save(goal);
                });
    }

    public LocalDate todayForUser(UUID userId) {
        return LocalDate.now(clock.withZone(resolveZone(userId)));
    }

    public LocalDate activityDateForUser(UUID userId, Instant instant) {
        if (instant == null) {
            return todayForUser(userId);
        }
        return instant.atZone(resolveZone(userId)).toLocalDate();
    }

    private Set<String> normalizeGoalTypes(Set<String> goalTypes) {
        return goalTypes.stream()
                .filter(goalType -> goalType != null && !goalType.isBlank())
                .map(goalType -> goalType.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private void evaluate(AnalyticsUserGoalProgressEntity goal, LocalDate today, Instant evaluatedAt) {
        String goalType = normalizeGoalType(goal.getGoalType());
        switch (goalType) {
            case CALORIES -> evaluateDailyCeiling(goal, dailyCalories(goal.getUserId(), today));
            case PROTEIN -> evaluateDailyFloor(goal, dailyProtein(goal.getUserId(), today));
            case CARBS -> evaluateDailyCeiling(goal, dailyCarbs(goal.getUserId(), today));
            case FAT -> evaluateDailyCeiling(goal, dailyFat(goal.getUserId(), today));
            case WORKOUT_FREQUENCY -> evaluateWorkoutFrequency(goal, today);
            case WEIGHT -> evaluateWeightPlaceholder(goal);
            default -> clearInactiveProgress(goal);
        }
        goal.setDaysRemaining(GoalExpiryPolicy.daysRemaining(goal.getTargetDate(), today));
        goal.setLastEvaluatedAt(evaluatedAt);
    }

    private void evaluateDailyFloor(AnalyticsUserGoalProgressEntity goal, BigDecimal currentValue) {
        BigDecimal current = currentValue == null ? BigDecimal.ZERO : currentValue;
        goal.setCurrentValue(current);
        BigDecimal ratio = ratio(current, goal.getTargetValue());
        goal.setProgressRatio(ratio);
        goal.setOnTrack(ratio != null && current.compareTo(goal.getTargetValue()) >= 0);
    }

    private void evaluateDailyCeiling(AnalyticsUserGoalProgressEntity goal, BigDecimal currentValue) {
        BigDecimal current = currentValue == null ? BigDecimal.ZERO : currentValue;
        goal.setCurrentValue(current);
        BigDecimal ratio = ratio(current, goal.getTargetValue());
        goal.setProgressRatio(ratio);
        goal.setOnTrack(ratio != null && current.compareTo(goal.getTargetValue()) <= 0);
    }

    private void evaluateWorkoutFrequency(AnalyticsUserGoalProgressEntity goal, LocalDate today) {
        LocalDate from = today.minusDays(6);
        BigDecimal current = BigDecimal.valueOf(dailyWorkoutRepository
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(goal.getUserId(), from, today)
                .stream()
                .mapToInt(row -> defaultInt(row.getCompletedWorkoutCount()))
                .sum());

        goal.setCurrentValue(current);
        BigDecimal ratio = ratio(current, goal.getTargetValue());
        goal.setProgressRatio(ratio);
        goal.setOnTrack(ratio != null && current.compareTo(requiredWorkoutPace(goal.getTargetValue(), today)) >= 0);
    }

    private void evaluateWeightPlaceholder(AnalyticsUserGoalProgressEntity goal) {
        BigDecimal current = dailyBodyRepository.findTopByUserIdOrderByMetricDateDesc(goal.getUserId())
                .map(AnalyticsUserDailyBodyEntity::getWeightKg)
                .orElse(null);
        goal.setCurrentValue(current);
        goal.setProgressRatio(null);
        goal.setOnTrack(false);
    }

    private BigDecimal dailyCalories(UUID userId, LocalDate today) {
        return dailyNutritionRepository.findByUserIdAndActivityDate(userId, today)
                .map(AnalyticsUserDailyNutritionEntity::getTotalCaloriesConsumed)
                .map(BigDecimal::valueOf)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal dailyProtein(UUID userId, LocalDate today) {
        return dailyNutritionRepository.findByUserIdAndActivityDate(userId, today)
                .map(AnalyticsUserDailyNutritionEntity::getTotalProteinGrams)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal dailyCarbs(UUID userId, LocalDate today) {
        return dailyNutritionRepository.findByUserIdAndActivityDate(userId, today)
                .map(AnalyticsUserDailyNutritionEntity::getTotalCarbsGrams)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal dailyFat(UUID userId, LocalDate today) {
        return dailyNutritionRepository.findByUserIdAndActivityDate(userId, today)
                .map(AnalyticsUserDailyNutritionEntity::getTotalFatGrams)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal ratio(BigDecimal currentValue, BigDecimal targetValue) {
        if (targetValue == null || targetValue.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return currentValue.divide(targetValue, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal requiredWorkoutPace(BigDecimal targetValue, LocalDate today) {
        int daysElapsedThisWeek = today.getDayOfWeek().getValue();
        return targetValue.multiply(BigDecimal.valueOf(daysElapsedThisWeek))
                .divide(SEVEN_DAYS, 0, RoundingMode.FLOOR);
    }

    private void clearInactiveProgress(AnalyticsUserGoalProgressEntity goal) {
        goal.setCurrentValue(null);
        goal.setProgressRatio(null);
        goal.setOnTrack(false);
    }

    private ZoneId resolveZone(UUID userId) {
        return dashboardSnapshotRepository.findById(userId)
                .map(snapshot -> parseZone(snapshot.getTimezone()))
                .orElse(DEFAULT_ZONE);
    }

    private ZoneId parseZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (DateTimeException ex) {
            return DEFAULT_ZONE;
        }
    }

    private String normalizeGoalType(String goalType) {
        return goalType == null ? "" : goalType.trim().toUpperCase(Locale.ROOT);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
}
