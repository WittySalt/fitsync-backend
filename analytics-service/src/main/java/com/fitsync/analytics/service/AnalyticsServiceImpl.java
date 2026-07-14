package com.fitsync.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

import com.fitsync.analytics.config.AnalyticsProperties;
import com.fitsync.analytics.dto.AnalyticsAchievementsResponse;
import com.fitsync.analytics.dto.AnalyticsActivityCalendarResponse;
import com.fitsync.analytics.dto.AnalyticsBodyTrendsResponse;
import com.fitsync.analytics.dto.AnalyticsCompareResponse;
import com.fitsync.analytics.dto.AnalyticsDashboardResponse;
import com.fitsync.analytics.dto.AnalyticsGoalProgressResponse;
import com.fitsync.analytics.dto.AnalyticsNutritionSeriesResponse;
import com.fitsync.analytics.dto.AnalyticsNutritionSummaryResponse;
import com.fitsync.analytics.dto.AnalyticsStreaksResponse;
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
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import com.fitsync.analytics.repository.AnalyticsUserStreakRollupRepository;
import com.fitsync.analytics.repository.AnalyticsUserWorkoutRecordsRepository;
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
    private final AnalyticsUserStreakRollupRepository streakRollupRepository;
    private final AnalyticsUserWorkoutRecordsRepository workoutRecordsRepository;
    private final Clock clock;
    private final AnalyticsProperties analyticsProperties;

    public AnalyticsServiceImpl(AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository,
                                AnalyticsUserGoalProgressRepository goalProgressRepository,
                                AnalyticsUserDailyBodyRepository dailyBodyRepository,
                                AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository,
                                AnalyticsUserDailyNutritionRepository dailyNutritionRepository,
                                AnalyticsUserStreakRollupRepository streakRollupRepository,
                                AnalyticsUserWorkoutRecordsRepository workoutRecordsRepository,
                                Clock clock,
                                AnalyticsProperties analyticsProperties) {
        this.dashboardSnapshotRepository = dashboardSnapshotRepository;
        this.goalProgressRepository = goalProgressRepository;
        this.dailyBodyRepository = dailyBodyRepository;
        this.dailyWorkoutRepository = dailyWorkoutRepository;
        this.dailyNutritionRepository = dailyNutritionRepository;
        this.streakRollupRepository = streakRollupRepository;
        this.workoutRecordsRepository = workoutRecordsRepository;
        this.clock = clock;
        this.analyticsProperties = analyticsProperties;
    }

    @Override
    @Cacheable(cacheNames = com.fitsync.analytics.config.CacheConfig.DASHBOARD_CACHE,
            key = "#principal.userId()")
    public AnalyticsDashboardResponse getDashboard(CurrentUserPrincipal principal) {
        AnalyticsUserDashboardSnapshotEntity snapshot = dashboardSnapshotRepository.findById(principal.userId())
                .orElse(null);
        List<AnalyticsDashboardResponse.GoalProgressSummary> goals =
                loadGoalSummaries(principal.userId(), GoalExpiryPolicy.ACTIVE_STATUS);

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
        validateDateRange(from, to, bucket);
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
        validateSummaryRange(from, to);
        WorkoutSummaryAggregation aggregation = aggregateWorkoutSummary(principal.userId(), from, to);
        Double averageDuration = aggregation.totalWorkoutCount() == 0
                ? null
                : ((double) aggregation.totalWorkoutMinutes()) / aggregation.totalWorkoutCount();
        int activeStreakDays = dashboardSnapshotRepository.findById(principal.userId())
                .map(snapshot -> defaultInteger(snapshot.getActiveStreakDays()))
                .orElse(0);

        return new AnalyticsWorkoutSummaryResponse(
                from,
                to,
                aggregation.totalWorkoutCount(),
                aggregation.totalWorkoutMinutes(),
                aggregation.totalCaloriesBurned(),
                aggregation.totalExerciseCount(),
                averageDuration,
                activeStreakDays);
    }

    @Override
    @Cacheable(cacheNames = com.fitsync.analytics.config.CacheConfig.WORKOUT_SERIES_CACHE,
            key = "#principal.userId() + ':' + #from + ':' + #to + ':' + #bucket.name()")
    public AnalyticsWorkoutSeriesResponse getWorkoutSeries(CurrentUserPrincipal principal,
                                                           LocalDate from,
                                                           LocalDate to,
                                                           BodyTrendBucket bucket) {
        validateDateRange(from, to, bucket);
        List<AnalyticsUserDailyWorkoutEntity> rows = dailyWorkoutRepository
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(principal.userId(), from, to);

        List<AnalyticsWorkoutSeriesResponse.WorkoutSeriesPoint> points = bucket == BodyTrendBucket.DAY
                ? rows.stream().map(this::toWorkoutSeriesPoint).toList()
                : aggregateWorkoutSeriesRows(rows, bucket);

        return new AnalyticsWorkoutSeriesResponse(from, to, bucket.name(), points);
    }

    @Override
    @Cacheable(cacheNames = com.fitsync.analytics.config.CacheConfig.ACTIVITY_CALENDAR_CACHE,
            key = "#principal.userId() + ':' + #from + ':' + #to")
    public AnalyticsActivityCalendarResponse getActivityCalendar(CurrentUserPrincipal principal,
                                                                 LocalDate from,
                                                                 LocalDate to) {
        validateSummaryRange(from, to);
        Map<LocalDate, AnalyticsUserDailyWorkoutEntity> byDate = new HashMap<>();
        for (AnalyticsUserDailyWorkoutEntity row : dailyWorkoutRepository
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(principal.userId(), from, to)) {
            byDate.put(row.getActivityDate(), row);
        }

        // Dense calendar: emit one point per day in range so inactive days are explicit zeros for the heatmap.
        List<AnalyticsActivityCalendarResponse.ActivityCalendarDay> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            AnalyticsUserDailyWorkoutEntity row = byDate.get(date);
            days.add(new AnalyticsActivityCalendarResponse.ActivityCalendarDay(
                    date,
                    row == null ? 0 : defaultInteger(row.getCompletedWorkoutCount()),
                    row == null ? 0 : defaultInteger(row.getTotalWorkoutMinutes())));
        }
        return new AnalyticsActivityCalendarResponse(from, to, days);
    }

    @Override
    @Cacheable(cacheNames = com.fitsync.analytics.config.CacheConfig.NUTRITION_SUMMARY_CACHE,
            key = "#principal.userId() + ':' + #from + ':' + #to")
    public AnalyticsNutritionSummaryResponse getNutritionSummary(CurrentUserPrincipal principal, LocalDate from, LocalDate to) {
        validateSummaryRange(from, to);
        NutritionSummaryAggregation aggregation = aggregateNutritionSummary(principal.userId(), from, to);

        return new AnalyticsNutritionSummaryResponse(
                from,
                to,
                aggregation.totalCaloriesConsumed(),
                toNullableDouble(aggregation.totalProteinGrams()),
                toNullableDouble(aggregation.totalCarbsGrams()),
                toNullableDouble(aggregation.totalFatGrams()),
                aggregation.totalCalorieTarget() == 0 ? null : aggregation.totalCalorieTarget(),
                aggregation.totalProteinTarget().compareTo(BigDecimal.ZERO) == 0
                        ? null
                        : aggregation.totalProteinTarget().doubleValue(),
                aggregation.averageCalorieAdherence(),
                aggregation.averageMacroAdherence(),
                aggregation.daysWithinBand());
    }

    @Override
    @Cacheable(cacheNames = com.fitsync.analytics.config.CacheConfig.NUTRITION_SERIES_CACHE,
            key = "#principal.userId() + ':' + #from + ':' + #to + ':' + #bucket.name()")
    public AnalyticsNutritionSeriesResponse getNutritionSeries(CurrentUserPrincipal principal,
                                                               LocalDate from,
                                                               LocalDate to,
                                                               BodyTrendBucket bucket) {
        validateDateRange(from, to, bucket);
        List<AnalyticsUserDailyNutritionEntity> rows = dailyNutritionRepository
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(principal.userId(), from, to);

        List<AnalyticsNutritionSeriesResponse.NutritionSeriesPoint> points = bucket == BodyTrendBucket.DAY
                ? rows.stream().map(this::toNutritionSeriesPoint).toList()
                : aggregateNutritionSeriesRows(rows, bucket);

        return new AnalyticsNutritionSeriesResponse(from, to, bucket.name(), points);
    }

    @Override
    @Cacheable(cacheNames = com.fitsync.analytics.config.CacheConfig.COMPARE_CACHE,
            key = "#principal.userId() + ':' + #period.name() + ':' + #root.target.todayUtcForCacheKey()")
    public AnalyticsCompareResponse getSummaryCompare(CurrentUserPrincipal principal, ComparePeriod period) {
        PeriodRanges ranges = compareRanges(todayUtcForCacheKey(), period);
        WorkoutSummaryAggregation currentWorkout =
                aggregateWorkoutSummary(principal.userId(), ranges.currentFrom(), ranges.currentTo());
        WorkoutSummaryAggregation previousWorkout =
                aggregateWorkoutSummary(principal.userId(), ranges.previousFrom(), ranges.previousTo());
        NutritionSummaryAggregation currentNutrition =
                aggregateNutritionSummary(principal.userId(), ranges.currentFrom(), ranges.currentTo());
        NutritionSummaryAggregation previousNutrition =
                aggregateNutritionSummary(principal.userId(), ranges.previousFrom(), ranges.previousTo());

        return new AnalyticsCompareResponse(
                period.name(),
                new AnalyticsCompareResponse.PeriodWindow(ranges.currentFrom(), ranges.currentTo()),
                new AnalyticsCompareResponse.PeriodWindow(ranges.previousFrom(), ranges.previousTo()),
                new AnalyticsCompareResponse.WorkoutComparison(
                        metricComparison(currentWorkout.totalWorkoutCount(), previousWorkout.totalWorkoutCount()),
                        metricComparison(currentWorkout.totalWorkoutMinutes(), previousWorkout.totalWorkoutMinutes()),
                        metricComparison(currentWorkout.totalCaloriesBurned(), previousWorkout.totalCaloriesBurned()),
                        metricComparison(currentWorkout.totalExerciseCount(), previousWorkout.totalExerciseCount())),
                new AnalyticsCompareResponse.NutritionComparison(
                        metricComparison(
                                currentNutrition.totalCaloriesConsumed(),
                                previousNutrition.totalCaloriesConsumed()),
                        nullableMetricComparison(
                                currentNutrition.totalProteinGrams(),
                                previousNutrition.totalProteinGrams()),
                        nullableMetricComparison(
                                currentNutrition.totalCarbsGrams(),
                                previousNutrition.totalCarbsGrams()),
                        nullableMetricComparison(
                                currentNutrition.totalFatGrams(),
                                previousNutrition.totalFatGrams())));
    }

    public LocalDate todayUtcForCacheKey() {
        return LocalDate.now(clock);
    }

    @Override
    @Cacheable(cacheNames = com.fitsync.analytics.config.CacheConfig.GOAL_PROGRESS_CACHE,
            key = "#principal.userId() + ':' + (#status == null ? '' : #status.toUpperCase(T(java.util.Locale).ROOT))")
    public AnalyticsGoalProgressResponse getGoalProgress(CurrentUserPrincipal principal, String status) {
        String normalizedStatus = normalizeStatus(status);
        // Collapse to one row per goal type, keeping the most recently evaluated entry.
        // The read model is keyed by goalId and can hold stale rows from superseded goals
        // (e.g. re-seeded ids), so without this guard the same type renders multiple times.
        Map<String, AnalyticsUserGoalProgressEntity> latestByGoalType = new HashMap<>();
        goalProgressRepository.findByUserIdOrderByTargetDateAscGoalIdAsc(principal.userId()).stream()
                .filter(goal -> normalizedStatus.isBlank()
                        || normalizedStatus.equals(normalizeStatus(goal.getGoalStatus())))
                .forEach(goal -> latestByGoalType.merge(
                        goal.getGoalType(),
                        goal,
                        (current, candidate) -> shouldReplaceGoalSummary(current, candidate) ? candidate : current));

        return new AnalyticsGoalProgressResponse(
                latestByGoalType.values().stream()
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

    /**
     * The current streak is read from the dashboard snapshot's {@code activeStreakDays}, so this
     * endpoint and {@code /dashboard} always report the same streak from the same projection code.
     * The best streak comes from the nightly AN-1 rollup; because that rollup can lag a same-day
     * streak extension by up to one day, a live current streak that exceeds the materialized best
     * is surfaced as the best, with its window derived from the last active date.
     */
    @Override
    @Cacheable(cacheNames = com.fitsync.analytics.config.CacheConfig.STREAKS_CACHE,
            key = "#principal.userId()")
    public AnalyticsStreaksResponse getStreaks(CurrentUserPrincipal principal) {
        AnalyticsUserDashboardSnapshotEntity snapshot = dashboardSnapshotRepository.findById(principal.userId())
                .orElse(null);
        int currentStreakDays = snapshot == null ? 0 : defaultInteger(snapshot.getActiveStreakDays());
        String timezone = snapshot == null || snapshot.getTimezone() == null
                ? DEFAULT_TIMEZONE
                : snapshot.getTimezone();

        LocalDate lastActiveDate = dailyWorkoutRepository
                .findTopByUserIdAndCompletedWorkoutCountGreaterThanOrderByActivityDateDesc(principal.userId(), 0)
                .map(AnalyticsUserDailyWorkoutEntity::getActivityDate)
                .orElse(null);

        AnalyticsUserStreakRollupEntity rollup = streakRollupRepository.findById(principal.userId()).orElse(null);
        int bestStreakDays = rollup == null ? 0 : defaultInteger(rollup.getBestStreakDays());
        LocalDate bestStreakFrom = rollup == null ? null : rollup.getBestStreakFrom();
        LocalDate bestStreakTo = rollup == null ? null : rollup.getBestStreakTo();
        if (currentStreakDays > bestStreakDays && lastActiveDate != null) {
            bestStreakDays = currentStreakDays;
            bestStreakFrom = lastActiveDate.minusDays(currentStreakDays - 1L);
            bestStreakTo = lastActiveDate;
        }

        return new AnalyticsStreaksResponse(
                currentStreakDays,
                bestStreakDays,
                bestStreakFrom,
                bestStreakTo,
                lastActiveDate,
                timezone);
    }

    /**
     * Achievements are derived read-side from the existing projections; there is no per-achievement
     * event stream. {@code unlockedAt} is therefore reconstructed: date-based achievements use the
     * start of the qualifying activity date in the user's timezone, streak achievements use the day
     * the best run reached the threshold, and PR_FIRST uses the first projected personal-record
     * instant. A zero-data user gets the full list with every achievement locked at progress 0.
     */
    @Override
    @Cacheable(cacheNames = com.fitsync.analytics.config.CacheConfig.ACHIEVEMENTS_CACHE,
            key = "#principal.userId()")
    public AnalyticsAchievementsResponse getAchievements(CurrentUserPrincipal principal) {
        UUID userId = principal.userId();
        AnalyticsUserDashboardSnapshotEntity snapshot = dashboardSnapshotRepository.findById(userId).orElse(null);
        ZoneId userZone = resolveZone(snapshot == null ? null : snapshot.getTimezone());
        int currentStreakDays = snapshot == null ? 0 : defaultInteger(snapshot.getActiveStreakDays());

        List<AnalyticsUserDailyWorkoutEntity> workoutRows = dailyWorkoutRepository
                .findByUserIdOrderByActivityDateAsc(userId);
        int totalWorkouts = workoutRows.stream()
                .mapToInt(row -> defaultInteger(row.getCompletedWorkoutCount()))
                .sum();
        LocalDate lastActiveDate = null;
        for (AnalyticsUserDailyWorkoutEntity row : workoutRows) {
            if (defaultInteger(row.getCompletedWorkoutCount()) > 0) {
                lastActiveDate = row.getActivityDate();
            }
        }

        AnalyticsUserStreakRollupEntity rollup = streakRollupRepository.findById(userId).orElse(null);
        int bestStreakDays = Math.max(
                rollup == null ? 0 : defaultInteger(rollup.getBestStreakDays()),
                currentStreakDays);

        LocalDate firstMealDate = dailyNutritionRepository
                .findTopByUserIdAndMealCountGreaterThanOrderByActivityDateAsc(userId, 0)
                .map(AnalyticsUserDailyNutritionEntity::getActivityDate)
                .orElse(null);
        LocalDate firstWeightDate = dailyBodyRepository
                .findTopByUserIdAndWeightKgIsNotNullOrderByMetricDateAsc(userId)
                .map(AnalyticsUserDailyBodyEntity::getMetricDate)
                .orElse(null);
        AnalyticsUserWorkoutRecordsEntity records = workoutRecordsRepository.findById(userId).orElse(null);
        int personalRecordCount = records == null ? 0 : defaultInteger(records.getPersonalRecordCount());

        List<AnalyticsAchievementsResponse.AchievementView> achievements = new ArrayList<>();
        achievements.add(workoutCountAchievement("FIRST_WORKOUT", "First workout", 1, workoutRows, totalWorkouts, userZone));
        achievements.add(workoutCountAchievement("WORKOUTS_10", "10 workouts", 10, workoutRows, totalWorkouts, userZone));
        achievements.add(workoutCountAchievement("WORKOUTS_50", "50 workouts", 50, workoutRows, totalWorkouts, userZone));
        achievements.add(workoutCountAchievement("WORKOUTS_100", "100 workouts", 100, workoutRows, totalWorkouts, userZone));
        achievements.add(streakAchievement("STREAK_7", "7-day streak", 7,
                bestStreakDays, rollup, currentStreakDays, lastActiveDate, userZone));
        achievements.add(streakAchievement("STREAK_30", "30-day streak", 30,
                bestStreakDays, rollup, currentStreakDays, lastActiveDate, userZone));
        achievements.add(firstEventAchievement("FIRST_MEAL_LOGGED", "First meal logged", firstMealDate, userZone));
        achievements.add(firstEventAchievement("FIRST_WEIGHT_LOGGED", "First weigh-in", firstWeightDate, userZone));
        achievements.add(new AnalyticsAchievementsResponse.AchievementView(
                "PR_FIRST",
                "First personal record",
                personalRecordCount > 0,
                personalRecordCount > 0 ? records.getFirstRecordAt() : null,
                achievementProgress(personalRecordCount, 1)));
        return new AnalyticsAchievementsResponse(achievements);
    }

    private AnalyticsAchievementsResponse.AchievementView workoutCountAchievement(
            String code,
            String title,
            int target,
            List<AnalyticsUserDailyWorkoutEntity> workoutRows,
            int totalWorkouts,
            ZoneId userZone) {
        boolean unlocked = totalWorkouts >= target;
        Instant unlockedAt = null;
        if (unlocked) {
            int cumulative = 0;
            for (AnalyticsUserDailyWorkoutEntity row : workoutRows) {
                cumulative += defaultInteger(row.getCompletedWorkoutCount());
                if (cumulative >= target) {
                    unlockedAt = startOfDay(row.getActivityDate(), userZone);
                    break;
                }
            }
        }
        return new AnalyticsAchievementsResponse.AchievementView(
                code, title, unlocked, unlockedAt, achievementProgress(totalWorkouts, target));
    }

    private AnalyticsAchievementsResponse.AchievementView streakAchievement(
            String code,
            String title,
            int target,
            int bestStreakDays,
            AnalyticsUserStreakRollupEntity rollup,
            int currentStreakDays,
            LocalDate lastActiveDate,
            ZoneId userZone) {
        boolean unlocked = bestStreakDays >= target;
        Instant unlockedAt = null;
        if (unlocked) {
            if (rollup != null && defaultInteger(rollup.getBestStreakDays()) >= target
                    && rollup.getBestStreakFrom() != null) {
                unlockedAt = startOfDay(rollup.getBestStreakFrom().plusDays(target - 1L), userZone);
            } else if (currentStreakDays >= target && lastActiveDate != null) {
                // Rollup lag: the live streak crossed the threshold before tonight's materialization.
                unlockedAt = startOfDay(lastActiveDate.minusDays(currentStreakDays - (long) target), userZone);
            }
        }
        return new AnalyticsAchievementsResponse.AchievementView(
                code, title, unlocked, unlockedAt, achievementProgress(bestStreakDays, target));
    }

    private AnalyticsAchievementsResponse.AchievementView firstEventAchievement(
            String code,
            String title,
            LocalDate firstDate,
            ZoneId userZone) {
        return new AnalyticsAchievementsResponse.AchievementView(
                code,
                title,
                firstDate != null,
                firstDate == null ? null : startOfDay(firstDate, userZone),
                firstDate != null ? 1.0 : 0.0);
    }

    private double achievementProgress(int value, int target) {
        if (value <= 0) {
            return 0.0;
        }
        if (value >= target) {
            return 1.0;
        }
        return BigDecimal.valueOf(value)
                .divide(BigDecimal.valueOf(target), 4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private Instant startOfDay(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant();
    }

    private ZoneId resolveZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (DateTimeException ex) {
            return ZoneOffset.UTC;
        }
    }

    private List<AnalyticsDashboardResponse.GoalProgressSummary> loadGoalSummaries(UUID userId, String status) {
        String normalizedStatus = normalizeStatus(status);
        Map<String, AnalyticsUserGoalProgressEntity> latestByGoalType = new HashMap<>();
        goalProgressRepository.findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(userId, normalizedStatus)
                .forEach(entity -> latestByGoalType.merge(
                        entity.getGoalType(),
                        entity,
                        (current, candidate) -> shouldReplaceGoalSummary(current, candidate) ? candidate : current));

        return latestByGoalType.values().stream()
                .sorted(Comparator.comparing(AnalyticsUserGoalProgressEntity::getTargetDate,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AnalyticsUserGoalProgressEntity::getGoalId))
                .map(this::toGoalProgressSummary)
                .toList();
    }

    private boolean shouldReplaceGoalSummary(AnalyticsUserGoalProgressEntity current,
                                             AnalyticsUserGoalProgressEntity candidate) {
        int evaluatedComparison = Comparator.nullsFirst(Instant::compareTo)
                .compare(candidate.getLastEvaluatedAt(), current.getLastEvaluatedAt());
        if (evaluatedComparison != 0) {
            return evaluatedComparison > 0;
        }
        return Comparator.nullsFirst(UUID::compareTo).compare(candidate.getGoalId(), current.getGoalId()) > 0;
    }

    private WorkoutSummaryAggregation aggregateWorkoutSummary(java.util.UUID userId, LocalDate from, LocalDate to) {
        List<AnalyticsUserDailyWorkoutEntity> rows = dailyWorkoutRepository
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, from, to);
        return new WorkoutSummaryAggregation(
                rows.stream().mapToInt(row -> defaultInteger(row.getCompletedWorkoutCount())).sum(),
                rows.stream().mapToInt(row -> defaultInteger(row.getTotalWorkoutMinutes())).sum(),
                rows.stream().mapToInt(row -> defaultInteger(row.getTotalCaloriesBurned())).sum(),
                rows.stream().mapToInt(row -> defaultInteger(row.getTotalExerciseCount())).sum());
    }

    private NutritionSummaryAggregation aggregateNutritionSummary(java.util.UUID userId, LocalDate from, LocalDate to) {
        List<AnalyticsUserDailyNutritionEntity> rows = dailyNutritionRepository
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, from, to);
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

        return new NutritionSummaryAggregation(
                rows.stream().mapToInt(row -> defaultInteger(row.getTotalCaloriesConsumed())).sum(),
                totalProteinGrams,
                totalCarbsGrams,
                totalFatGrams,
                rows.stream()
                        .map(AnalyticsUserDailyNutritionEntity::getCalorieTarget)
                        .filter(Objects::nonNull)
                        .mapToInt(Integer::intValue)
                        .sum(),
                totalProteinTarget,
                average(calorieRatios),
                average(proteinScores),
                daysWithinBand);
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
     * Builds compare windows from the UTC date supplied by the injected clock. WEEK uses ISO-Monday
     * calendar weeks and compares the elapsed part of this week with the same weekday span last week.
     * MONTH compares the elapsed part of this calendar month with the same day-of-month span last
     * month; when the previous month is shorter, the previous end date is clamped to that month's
     * final day. That clamp is intentional and makes month-end comparisons behave as "this month vs
     * last month", even when the window lengths differ.
     */
    private PeriodRanges compareRanges(LocalDate todayUtc, ComparePeriod period) {
        return switch (period) {
            case WEEK -> {
                LocalDate currentFrom = todayUtc.with(WeekFields.ISO.dayOfWeek(), 1);
                long elapsedDays = ChronoUnit.DAYS.between(currentFrom, todayUtc);
                LocalDate previousFrom = currentFrom.minusWeeks(1);
                yield new PeriodRanges(currentFrom, todayUtc, previousFrom, previousFrom.plusDays(elapsedDays));
            }
            case MONTH -> {
                LocalDate currentFrom = todayUtc.withDayOfMonth(1);
                LocalDate previousFrom = currentFrom.minusMonths(1);
                int previousEndDay = Math.min(todayUtc.getDayOfMonth(), previousFrom.lengthOfMonth());
                yield new PeriodRanges(
                        currentFrom,
                        todayUtc,
                        previousFrom,
                        previousFrom.withDayOfMonth(previousEndDay));
            }
        };
    }

    private AnalyticsCompareResponse.MetricComparison metricComparison(int current, int previous) {
        return metricComparison(BigDecimal.valueOf(current), BigDecimal.valueOf(previous), false);
    }

    private AnalyticsCompareResponse.MetricComparison nullableMetricComparison(
            BigDecimal current,
            BigDecimal previous) {
        return metricComparison(current, previous, true);
    }

    private AnalyticsCompareResponse.MetricComparison metricComparison(
            BigDecimal current,
            BigDecimal previous,
            boolean nullWhenBothMissing) {
        BigDecimal delta = current.subtract(previous);
        boolean bothZero = current.compareTo(BigDecimal.ZERO) == 0 && previous.compareTo(BigDecimal.ZERO) == 0;
        // Nullable nutrition gram metrics use zero as "no data"; no-data vs no-data should not show a zero delta.
        Double visibleDelta = nullWhenBothMissing && bothZero ? null : delta.doubleValue();
        return new AnalyticsCompareResponse.MetricComparison(
                nullWhenBothMissing ? toNullableDouble(current) : Double.valueOf(current.doubleValue()),
                nullWhenBothMissing ? toNullableDouble(previous) : Double.valueOf(previous.doubleValue()),
                visibleDelta,
                percentageDelta(delta, previous));
    }

    private Double percentageDelta(BigDecimal delta, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return delta.multiply(BigDecimal.valueOf(100))
                .divide(previous, 4, RoundingMode.HALF_UP)
                .doubleValue();
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

    private List<AnalyticsWorkoutSeriesResponse.WorkoutSeriesPoint> aggregateWorkoutSeriesRows(
            List<AnalyticsUserDailyWorkoutEntity> rows,
            BodyTrendBucket bucket) {
        Map<LocalDate, WorkoutSeriesBucketAccumulator> grouped = new TreeMap<>();
        rows.forEach(row -> grouped
                .computeIfAbsent(bucketStart(row.getActivityDate(), bucket), WorkoutSeriesBucketAccumulator::new)
                .accept(row));
        return grouped.values().stream()
                .map(WorkoutSeriesBucketAccumulator::toPoint)
                .toList();
    }

    private AnalyticsWorkoutSeriesResponse.WorkoutSeriesPoint toWorkoutSeriesPoint(
            AnalyticsUserDailyWorkoutEntity row) {
        return new AnalyticsWorkoutSeriesResponse.WorkoutSeriesPoint(
                row.getActivityDate(),
                defaultInteger(row.getCompletedWorkoutCount()),
                defaultInteger(row.getTotalWorkoutMinutes()),
                defaultInteger(row.getTotalCaloriesBurned()),
                defaultInteger(row.getTotalExerciseCount()));
    }

    private List<AnalyticsNutritionSeriesResponse.NutritionSeriesPoint> aggregateNutritionSeriesRows(
            List<AnalyticsUserDailyNutritionEntity> rows,
            BodyTrendBucket bucket) {
        Map<LocalDate, NutritionSeriesBucketAccumulator> grouped = new TreeMap<>();
        rows.forEach(row -> grouped
                .computeIfAbsent(bucketStart(row.getActivityDate(), bucket), NutritionSeriesBucketAccumulator::new)
                .accept(row));
        return grouped.values().stream()
                .map(NutritionSeriesBucketAccumulator::toPoint)
                .toList();
    }

    private AnalyticsNutritionSeriesResponse.NutritionSeriesPoint toNutritionSeriesPoint(
            AnalyticsUserDailyNutritionEntity row) {
        return new AnalyticsNutritionSeriesResponse.NutritionSeriesPoint(
                row.getActivityDate(),
                defaultInteger(row.getTotalCaloriesConsumed()),
                toNullableDouble(row.getTotalProteinGrams()),
                toNullableDouble(row.getTotalCarbsGrams()),
                toNullableDouble(row.getTotalFatGrams()),
                nullableNonZeroInteger(row.getCalorieTarget()),
                toNullableDouble(row.getProteinTargetGrams()));
    }

    private LocalDate bucketStart(LocalDate date, BodyTrendBucket bucket) {
        return switch (bucket) {
            case DAY -> date;
            case WEEK -> date.with(WeekFields.ISO.dayOfWeek(), 1);
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    /**
     * Validates trend ranges using one ChronoUnit.DAYS day-span cap per bucket. WEEK and MONTH
     * are allowed longer raw date spans than DAY because they return coarser buckets instead of daily points.
     */
    private void validateDateRange(LocalDate from, LocalDate to, BodyTrendBucket bucket) {
        long rangeDays = validateBaseDateRange(from, to);
        int maxRangeDays = maxRangeDaysFor(bucket);
        if (rangeDays > maxRangeDays) {
            throw new BadRequestException(
                    "Date range for " + bucket.name() + " bucket must not exceed " + maxRangeDays + " days");
        }
    }

    /**
     * Validates summary ranges with the DAY cap because summary endpoints do not have bucket semantics.
     */
    private void validateSummaryRange(LocalDate from, LocalDate to) {
        long rangeDays = validateBaseDateRange(from, to);
        int maxRangeDays = analyticsProperties.trends().maxRangeDays().day();
        if (rangeDays > maxRangeDays) {
            throw new BadRequestException("Date range must not exceed " + maxRangeDays + " days");
        }
    }

    private long validateBaseDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BadRequestException("from and to are required");
        }
        if (to.isBefore(from)) {
            throw new BadRequestException("to must not be before from");
        }
        return ChronoUnit.DAYS.between(from, to);
    }

    private int maxRangeDaysFor(BodyTrendBucket bucket) {
        AnalyticsProperties.MaxRangeDays maxRangeDays = analyticsProperties.trends().maxRangeDays();
        return switch (bucket) {
            case DAY -> maxRangeDays.day();
            case WEEK -> maxRangeDays.week();
            case MONTH -> maxRangeDays.month();
        };
    }

    private AnalyticsDashboardResponse.GoalProgressSummary toGoalProgressSummary(
            AnalyticsUserGoalProgressEntity entity) {
        return new AnalyticsDashboardResponse.GoalProgressSummary(
                entity.getGoalId(),
                entity.getGoalType(),
                toDouble(entity.getTargetValue()),
                entity.getTargetUnit(),
                entity.getTargetDate(),
                toDouble(entity.getCurrentValue()),
                toDouble(entity.getProgressRatio()),
                entity.isOnTrack(),
                entity.getLastEvaluatedAt());
    }

    private Double average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(values.size()), 4, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    private static Double toNullableDouble(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) == 0 ? null : value.doubleValue();
    }

    private static Double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }

    private static int defaultInteger(Integer value) {
        return value != null ? value : 0;
    }

    private static Integer nullableNonZeroInteger(Integer value) {
        return value == null || value == 0 ? null : value;
    }

    private String normalizeStatus(String status) {
        return status == null ? "" : status.toUpperCase(Locale.ROOT);
    }

    private record PeriodRanges(
            LocalDate currentFrom,
            LocalDate currentTo,
            LocalDate previousFrom,
            LocalDate previousTo) {
    }

    private record WorkoutSummaryAggregation(
            int totalWorkoutCount,
            int totalWorkoutMinutes,
            int totalCaloriesBurned,
            int totalExerciseCount) {
    }

    private record NutritionSummaryAggregation(
            int totalCaloriesConsumed,
            BigDecimal totalProteinGrams,
            BigDecimal totalCarbsGrams,
            BigDecimal totalFatGrams,
            int totalCalorieTarget,
            BigDecimal totalProteinTarget,
            Double averageCalorieAdherence,
            Double averageMacroAdherence,
            int daysWithinBand) {
    }

    private static final class WorkoutSeriesBucketAccumulator {

        private final LocalDate bucketStart;
        private int completedWorkoutCount;
        private int totalWorkoutMinutes;
        private int totalCaloriesBurned;
        private int totalExerciseCount;

        WorkoutSeriesBucketAccumulator(LocalDate bucketStart) {
            this.bucketStart = bucketStart;
        }

        void accept(AnalyticsUserDailyWorkoutEntity row) {
            completedWorkoutCount += defaultInteger(row.getCompletedWorkoutCount());
            totalWorkoutMinutes += defaultInteger(row.getTotalWorkoutMinutes());
            totalCaloriesBurned += defaultInteger(row.getTotalCaloriesBurned());
            totalExerciseCount += defaultInteger(row.getTotalExerciseCount());
        }

        AnalyticsWorkoutSeriesResponse.WorkoutSeriesPoint toPoint() {
            return new AnalyticsWorkoutSeriesResponse.WorkoutSeriesPoint(
                    bucketStart,
                    completedWorkoutCount,
                    totalWorkoutMinutes,
                    totalCaloriesBurned,
                    totalExerciseCount);
        }
    }

    private static final class NutritionSeriesBucketAccumulator {

        private final LocalDate bucketStart;
        private int totalCaloriesConsumed;
        private BigDecimal totalProteinGrams = BigDecimal.ZERO;
        private BigDecimal totalCarbsGrams = BigDecimal.ZERO;
        private BigDecimal totalFatGrams = BigDecimal.ZERO;
        private int calorieTarget;
        private BigDecimal proteinTargetGrams = BigDecimal.ZERO;

        NutritionSeriesBucketAccumulator(LocalDate bucketStart) {
            this.bucketStart = bucketStart;
        }

        void accept(AnalyticsUserDailyNutritionEntity row) {
            totalCaloriesConsumed += defaultInteger(row.getTotalCaloriesConsumed());
            totalProteinGrams = addIfPresent(totalProteinGrams, row.getTotalProteinGrams());
            totalCarbsGrams = addIfPresent(totalCarbsGrams, row.getTotalCarbsGrams());
            totalFatGrams = addIfPresent(totalFatGrams, row.getTotalFatGrams());
            calorieTarget += defaultInteger(row.getCalorieTarget());
            proteinTargetGrams = addIfPresent(proteinTargetGrams, row.getProteinTargetGrams());
        }

        AnalyticsNutritionSeriesResponse.NutritionSeriesPoint toPoint() {
            return new AnalyticsNutritionSeriesResponse.NutritionSeriesPoint(
                    bucketStart,
                    totalCaloriesConsumed,
                    toNullableDouble(totalProteinGrams),
                    toNullableDouble(totalCarbsGrams),
                    toNullableDouble(totalFatGrams),
                    nullableNonZeroInteger(calorieTarget),
                    toNullableDouble(proteinTargetGrams));
        }

        private BigDecimal addIfPresent(BigDecimal currentValue, BigDecimal newValue) {
            return newValue == null ? currentValue : currentValue.add(newValue);
        }
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
