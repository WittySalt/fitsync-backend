package com.fitsync.analytics.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.fitsync.analytics.dto.AnalyticsBodyTrendsResponse;
import com.fitsync.analytics.dto.AnalyticsDashboardResponse;
import com.fitsync.analytics.dto.AnalyticsGoalProgressResponse;
import com.fitsync.analytics.dto.AnalyticsNutritionSummaryResponse;
import com.fitsync.analytics.dto.AnalyticsWorkoutSummaryResponse;
import com.fitsync.analytics.dto.BodyTrendBucket;
import com.fitsync.analytics.entity.AnalyticsUserDailyBodyEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyWorkoutEntity;
import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import com.fitsync.analytics.entity.AnalyticsUserGoalProgressEntity;
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import com.fitsync.analytics.security.CurrentUserPrincipal;
import com.fitsync.web.error.BadRequestException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final String DEFAULT_TIMEZONE = "UTC";

    private final AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository;
    private final AnalyticsUserGoalProgressRepository goalProgressRepository;
    private final AnalyticsUserDailyBodyRepository dailyBodyRepository;
    private final AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository;
    private final AnalyticsUserDailyNutritionRepository dailyNutritionRepository;
    private final Clock clock;

    public AnalyticsServiceImpl(AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository,
                                AnalyticsUserGoalProgressRepository goalProgressRepository,
                                AnalyticsUserDailyBodyRepository dailyBodyRepository,
                                AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository,
                                AnalyticsUserDailyNutritionRepository dailyNutritionRepository,
                                Clock clock) {
        this.dashboardSnapshotRepository = dashboardSnapshotRepository;
        this.goalProgressRepository = goalProgressRepository;
        this.dailyBodyRepository = dailyBodyRepository;
        this.dailyWorkoutRepository = dailyWorkoutRepository;
        this.dailyNutritionRepository = dailyNutritionRepository;
        this.clock = clock;
    }

    @Override
    @Cacheable(cacheNames = com.fitsync.analytics.config.CacheConfig.DASHBOARD_CACHE,
            key = "#principal.userId()")
    public AnalyticsDashboardResponse getDashboard(CurrentUserPrincipal principal) {
        AnalyticsUserDashboardSnapshotEntity snapshot = dashboardSnapshotRepository.findById(principal.userId())
                .orElse(null);
        List<AnalyticsDashboardResponse.GoalProgressSummary> goals = loadGoalSummaries(principal.userId(), null);

        if (snapshot == null) {
            return emptyDashboard(goals);
        }

        return new AnalyticsDashboardResponse(
                snapshot.getGeneratedAt(),
                snapshot.getTimezone() != null ? snapshot.getTimezone() : DEFAULT_TIMEZONE,
                new AnalyticsDashboardResponse.BodySummary(
                        toDouble(snapshot.getLatestWeightKg()),
                        toDouble(snapshot.getLatestBodyFatRate()),
                        toDouble(snapshot.getBmi()),
                        toDouble(snapshot.getWeightChange30d())),
                new AnalyticsDashboardResponse.WorkoutSummary(
                        defaultInteger(snapshot.getTodayWorkoutCount()),
                        defaultInteger(snapshot.getWeekWorkoutCount()),
                        defaultInteger(snapshot.getWeekWorkoutMinutes()),
                        defaultInteger(snapshot.getActiveStreakDays())),
                new AnalyticsDashboardResponse.NutritionSummary(
                        snapshot.getTodayCaloriesConsumed(),
                        snapshot.getTodayCaloriesTarget(),
                        toDouble(snapshot.getTodayProteinGrams()),
                        toDouble(snapshot.getTodayProteinTargetGrams())),
                goals);
    }

    @Override
    @Cacheable(cacheNames = com.fitsync.analytics.config.CacheConfig.BODY_TRENDS_CACHE,
            key = "#principal.userId() + ':' + #from + ':' + #to + ':' + #bucket.name()")
    public AnalyticsBodyTrendsResponse getBodyTrends(CurrentUserPrincipal principal,
                                                     LocalDate from,
                                                     LocalDate to,
                                                     BodyTrendBucket bucket) {
        validateDateRange(from, to);
        List<AnalyticsUserDailyBodyEntity> rows = dailyBodyRepository
                .findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(principal.userId(), from, to);

        List<AnalyticsBodyTrendsResponse.BodyTrendPoint> points = bucket == BodyTrendBucket.DAY
                ? rows.stream().map(this::toBodyTrendPoint).toList()
                : aggregateBodyTrendRows(rows, bucket);

        return new AnalyticsBodyTrendsResponse(from, to, bucket.name(), points);
    }

    @Override
    @Cacheable(cacheNames = com.fitsync.analytics.config.CacheConfig.WORKOUT_SUMMARY_CACHE,
            key = "#principal.userId() + ':' + #from + ':' + #to")
    public AnalyticsWorkoutSummaryResponse getWorkoutSummary(CurrentUserPrincipal principal, LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        List<AnalyticsUserDailyWorkoutEntity> rows = dailyWorkoutRepository
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(principal.userId(), from, to);
        int totalWorkoutCount = rows.stream().mapToInt(row -> defaultInteger(row.getCompletedWorkoutCount())).sum();
        int totalWorkoutMinutes = rows.stream().mapToInt(row -> defaultInteger(row.getTotalWorkoutMinutes())).sum();
        int totalCaloriesBurned = rows.stream().mapToInt(row -> defaultInteger(row.getTotalCaloriesBurned())).sum();
        int totalExerciseCount = rows.stream().mapToInt(row -> defaultInteger(row.getTotalExerciseCount())).sum();
        Double averageDuration = totalWorkoutCount == 0 ? null : ((double) totalWorkoutMinutes) / totalWorkoutCount;
        int activeStreakDays = dashboardSnapshotRepository.findById(principal.userId())
                .map(snapshot -> defaultInteger(snapshot.getActiveStreakDays()))
                .orElse(0);

        return new AnalyticsWorkoutSummaryResponse(
                from,
                to,
                totalWorkoutCount,
                totalWorkoutMinutes,
                totalCaloriesBurned,
                totalExerciseCount,
                averageDuration,
                activeStreakDays);
    }

    @Override
    @Cacheable(cacheNames = com.fitsync.analytics.config.CacheConfig.NUTRITION_SUMMARY_CACHE,
            key = "#principal.userId() + ':' + #from + ':' + #to")
    public AnalyticsNutritionSummaryResponse getNutritionSummary(CurrentUserPrincipal principal, LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        List<AnalyticsUserDailyNutritionEntity> rows = dailyNutritionRepository
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(principal.userId(), from, to);
        int totalCaloriesConsumed = rows.stream().mapToInt(row -> defaultInteger(row.getTotalCaloriesConsumed())).sum();
        BigDecimal totalProteinGrams = rows.stream()
                .map(AnalyticsUserDailyNutritionEntity::getTotalProteinGrams)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCarbsGrams = rows.stream()
                .map(AnalyticsUserDailyNutritionEntity::getTotalCarbsGrams)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalFatGrams = rows.stream()
                .map(AnalyticsUserDailyNutritionEntity::getTotalFatGrams)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalCalorieTarget = rows.stream()
                .map(AnalyticsUserDailyNutritionEntity::getCalorieTarget)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        BigDecimal totalProteinTarget = rows.stream()
                .map(AnalyticsUserDailyNutritionEntity::getProteinTargetGrams)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> calorieRatios = rows.stream()
                .map(AnalyticsUserDailyNutritionEntity::getCalorieAdherenceRatio)
                .filter(Objects::nonNull)
                .toList();
        List<BigDecimal> proteinScores = rows.stream()
                .map(AnalyticsUserDailyNutritionEntity::getMacroAdherenceScore)
                .filter(Objects::nonNull)
                .toList();
        int daysWithinBand = (int) calorieRatios.stream()
                .filter(ratio -> ratio.compareTo(new BigDecimal("0.90")) >= 0
                        && ratio.compareTo(new BigDecimal("1.10")) <= 0)
                .count();

        return new AnalyticsNutritionSummaryResponse(
                from,
                to,
                totalCaloriesConsumed,
                toNullableDouble(totalProteinGrams),
                toNullableDouble(totalCarbsGrams),
                toNullableDouble(totalFatGrams),
                totalCalorieTarget == 0 ? null : totalCalorieTarget,
                totalProteinTarget.compareTo(BigDecimal.ZERO) == 0 ? null : totalProteinTarget.doubleValue(),
                average(calorieRatios),
                average(proteinScores),
                daysWithinBand);
    }

    @Override
    @Cacheable(cacheNames = com.fitsync.analytics.config.CacheConfig.GOAL_PROGRESS_CACHE,
            key = "#principal.userId() + ':' + (#status == null ? '' : #status.toUpperCase(T(java.util.Locale).ROOT))")
    public AnalyticsGoalProgressResponse getGoalProgress(CurrentUserPrincipal principal, String status) {
        String normalizedStatus = normalizeStatus(status);
        return new AnalyticsGoalProgressResponse(
                goalProgressRepository.findByUserIdOrderByTargetDateAscGoalIdAsc(principal.userId()).stream()
                        .filter(goal -> normalizedStatus.isBlank()
                                || normalizedStatus.equals(normalizeStatus(goal.getGoalStatus())))
                        .sorted(Comparator.comparing(AnalyticsUserGoalProgressEntity::getTargetDate,
                                        Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(AnalyticsUserGoalProgressEntity::getGoalId))
                        .map(goal -> new AnalyticsGoalProgressResponse.GoalProgressView(
                                goal.getGoalId(),
                                goal.getGoalType(),
                                goal.getGoalStatus(),
                                toDouble(goal.getTargetValue()),
                                goal.getTargetUnit(),
                                goal.getTargetDate(),
                                toDouble(goal.getCurrentValue()),
                                toDouble(goal.getProgressRatio()),
                                goal.getDaysRemaining(),
                                goal.isOnTrack(),
                                goal.getLastEvaluatedAt()))
                        .toList());
    }

    private List<AnalyticsDashboardResponse.GoalProgressSummary> loadGoalSummaries(java.util.UUID userId, String status) {
        String normalizedStatus = normalizeStatus(status);
        return goalProgressRepository.findByUserIdOrderByTargetDateAscGoalIdAsc(userId)
                .stream()
                .filter(entity -> normalizedStatus.isBlank()
                        || normalizedStatus.equals(normalizeStatus(entity.getGoalStatus())))
                .sorted(Comparator.comparing(AnalyticsUserGoalProgressEntity::getTargetDate,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AnalyticsUserGoalProgressEntity::getGoalId))
                .map(this::toGoalProgressSummary)
                .toList();
    }

    private AnalyticsDashboardResponse emptyDashboard(List<AnalyticsDashboardResponse.GoalProgressSummary> goals) {
        return new AnalyticsDashboardResponse(
                Instant.now(clock),
                DEFAULT_TIMEZONE,
                new AnalyticsDashboardResponse.BodySummary(null, null, null, null),
                new AnalyticsDashboardResponse.WorkoutSummary(0, 0, 0, 0),
                new AnalyticsDashboardResponse.NutritionSummary(null, null, null, null),
                goals);
    }

    /**
     * Aggregates WEEK and MONTH body trend buckets using last-valid-value semantics. Each point-in-time
     * metric (weight, body-fat, waist, BMI, and delta-from-start) keeps the latest non-null value within
     * the bucket, so the point represents the bucket-end known state. The delta-from-previous field is
     * recomputed between adjacent aggregated bucket weights and is not copied from any source row.
     */
    private List<AnalyticsBodyTrendsResponse.BodyTrendPoint> aggregateBodyTrendRows(
            List<AnalyticsUserDailyBodyEntity> rows,
            BodyTrendBucket bucket) {
        Map<LocalDate, BodyTrendBucketAccumulator> grouped = new TreeMap<>();
        rows.forEach(row -> grouped
                .computeIfAbsent(bucketStart(row.getMetricDate(), bucket), BodyTrendBucketAccumulator::new)
                .accept(row));

        List<AnalyticsBodyTrendsResponse.BodyTrendPoint> points = new ArrayList<>();
        BigDecimal previousWeight = null;
        for (BodyTrendBucketAccumulator accumulator : grouped.values()) {
            BigDecimal deltaFromPrevious = accumulator.weightKg != null && previousWeight != null
                    ? accumulator.weightKg.subtract(previousWeight)
                    : null;
            points.add(accumulator.toPoint(deltaFromPrevious));
            previousWeight = accumulator.weightKg;
        }
        return points;
    }

    private AnalyticsBodyTrendsResponse.BodyTrendPoint toBodyTrendPoint(AnalyticsUserDailyBodyEntity row) {
        return new AnalyticsBodyTrendsResponse.BodyTrendPoint(
                row.getMetricDate(),
                toDouble(row.getWeightKg()),
                toDouble(row.getBodyFatRate()),
                toDouble(row.getWaistCm()),
                toDouble(row.getBmi()),
                toDouble(row.getWeightDeltaFromStart()),
                toDouble(row.getWeightDeltaFromPrevious()));
    }

    private LocalDate bucketStart(LocalDate date, BodyTrendBucket bucket) {
        return switch (bucket) {
            case DAY -> date;
            case WEEK -> date.with(WeekFields.ISO.dayOfWeek(), 1);
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BadRequestException("from and to are required");
        }
        if (to.isBefore(from)) {
            throw new BadRequestException("to must not be before from");
        }
    }

    private AnalyticsDashboardResponse.GoalProgressSummary toGoalProgressSummary(
            AnalyticsUserGoalProgressEntity entity) {
        return new AnalyticsDashboardResponse.GoalProgressSummary(
                entity.getGoalId(),
                entity.getGoalType(),
                toDouble(entity.getProgressRatio()),
                entity.isOnTrack());
    }

    private Double average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(values.size()), 4, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    private Double toNullableDouble(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) == 0 ? null : value.doubleValue();
    }

    private static Double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }

    private int defaultInteger(Integer value) {
        return value != null ? value : 0;
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.toUpperCase(Locale.ROOT);
    }

    private static final class BodyTrendBucketAccumulator {

        private final LocalDate bucketStart;
        private BigDecimal weightKg;
        private BigDecimal bodyFatRate;
        private BigDecimal waistCm;
        private BigDecimal bmi;
        private BigDecimal weightDeltaFromStart;

        BodyTrendBucketAccumulator(LocalDate bucketStart) {
            this.bucketStart = bucketStart;
        }

        void accept(AnalyticsUserDailyBodyEntity row) {
            weightKg = lastValid(weightKg, row.getWeightKg());
            bodyFatRate = lastValid(bodyFatRate, row.getBodyFatRate());
            waistCm = lastValid(waistCm, row.getWaistCm());
            bmi = lastValid(bmi, row.getBmi());
            weightDeltaFromStart = lastValid(weightDeltaFromStart, row.getWeightDeltaFromStart());
        }

        AnalyticsBodyTrendsResponse.BodyTrendPoint toPoint(BigDecimal weightDeltaFromPrevious) {
            return new AnalyticsBodyTrendsResponse.BodyTrendPoint(
                    bucketStart,
                    toDouble(weightKg),
                    toDouble(bodyFatRate),
                    toDouble(waistCm),
                    toDouble(bmi),
                    toDouble(weightDeltaFromStart),
                    toDouble(weightDeltaFromPrevious));
        }

        private BigDecimal lastValid(BigDecimal currentValue, BigDecimal newValue) {
            return newValue != null ? newValue : currentValue;
        }
    }
}
