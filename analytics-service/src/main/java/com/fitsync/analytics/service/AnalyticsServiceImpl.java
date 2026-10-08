package com.fitsync.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
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
import com.fitsync.analytics.dto.AnalyticsActivityCalendarResponse;
import com.fitsync.analytics.dto.AnalyticsBodyTrendsResponse;
import com.fitsync.analytics.dto.AnalyticsCompareResponse;
import com.fitsync.analytics.dto.AnalyticsDashboardResponse;
import com.fitsync.analytics.dto.AnalyticsGoalProgressResponse;
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
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import com.fitsync.analytics.repository.AnalyticsUserStreakRollupRepository;
import com.fitsync.analytics.security.CurrentUserPrincipal;
import com.fitsync.web.error.BadRequestException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Serves the dashboard, trend, summary, compare, goal, streak and weekly-report reads from the
 * event-built read models (daily rows, dashboard snapshot, goal rows, streak rollup). The stored
 * rows are only rewritten when an event arrives, so anything that means "today", "this week" or
 * "current" is re-checked against the clock in the user's stored zone at read time. Results are
 * cached per user; keys that depend on "today" include the user's local date so they roll over at
 * the user's midnight.
 */
@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final String DEFAULT_TIMEZONE = "UTC";

    /** A day counts as "within band" when calories consumed land within 10% of the calorie target. */
    private static final BigDecimal CALORIE_BAND_LOW = new BigDecimal("0.90");
    private static final BigDecimal CALORIE_BAND_HIGH = new BigDecimal("1.10");

    private final AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository;
    private final AnalyticsUserGoalProgressRepository goalProgressRepository;
    private final AnalyticsUserDailyBodyRepository dailyBodyRepository;
    private final AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository;
    private final AnalyticsUserDailyNutritionRepository dailyNutritionRepository;
    private final AnalyticsUserStreakRollupRepository streakRollupRepository;
    private final Clock clock;
    private final AnalyticsProperties analyticsProperties;

    public AnalyticsServiceImpl(AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository,
                                AnalyticsUserGoalProgressRepository goalProgressRepository,
                                AnalyticsUserDailyBodyRepository dailyBodyRepository,
                                AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository,
                                AnalyticsUserDailyNutritionRepository dailyNutritionRepository,
                                AnalyticsUserStreakRollupRepository streakRollupRepository,
                                Clock clock,
                                AnalyticsProperties analyticsProperties) {
        this.dashboardSnapshotRepository = dashboardSnapshotRepository;
        this.goalProgressRepository = goalProgressRepository;
        this.dailyBodyRepository = dailyBodyRepository;
        this.dailyWorkoutRepository = dailyWorkoutRepository;
        this.dailyNutritionRepository = dailyNutritionRepository;
        this.streakRollupRepository = streakRollupRepository;
        this.clock = clock;
        this.analyticsProperties = analyticsProperties;
    }

    @Override
    @Cacheable(cacheNames = com.fitsync.analytics.config.CacheConfig.DASHBOARD_CACHE,
            key = "#principal.userId() + ':' + #root.target.todayForCacheKey(#principal)")
    public AnalyticsDashboardResponse getDashboard(CurrentUserPrincipal principal) {
        AnalyticsUserDashboardSnapshotEntity snapshot = dashboardSnapshotRepository.findById(principal.userId())
                .orElse(null);
        ZoneId zone = UserZone.of(snapshot == null ? null : snapshot.getTimezone());
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LiveNutritionDay todayNutrition = new LiveNutritionDay(principal.userId(), today);
        List<AnalyticsDashboardResponse.GoalProgressSummary> goals =
                loadGoalSummaries(principal.userId(), GoalExpiryPolicy.ACTIVE_STATUS, today, todayNutrition);

        if (snapshot == null) {
            return emptyDashboard(goals);
        }

        // The snapshot is only rewritten when an event arrives, so a user who stops logging keeps
        // whatever it last held. Fields named "today" and "week" therefore have to be re-checked
        // against the clock at read time, or they report months-old activity as current. See
        // Documents/architecture/functional-defects-2026-09-26.md, defect 4.
        // Those fields are not taken from the snapshot at all. Checking its generatedAt against today
        // was not enough: every event and the nightly rebuild move generatedAt, but each rewrites only
        // its own fields, so a meal logged this morning dated yesterday's workout count as today's.
        // Today's calories and protein come from today's daily nutrition row in the user's zone (the
        // same row the live goal values use), the workout counts from the daily workout rows (see
        // workoutSummary), and both read 0 when there are no rows. The cache key carries the user's
        // local date and every meal and workout event evicts the user's entries, so the cached
        // response cannot outlive those rows or that day.

        return new AnalyticsDashboardResponse(
                snapshot.getGeneratedAt(),
                snapshot.getTimezone() != null ? snapshot.getTimezone() : DEFAULT_TIMEZONE,
                new AnalyticsDashboardResponse.BodySummary(
                        toDouble(snapshot.getLatestWeightKg()),
                        toDouble(snapshot.getLatestBodyFatRate()),
                        toDouble(snapshot.getBmi()),
                        toDouble(snapshot.getWeightChange30d()),
                        latestBodyMeasurementDate(principal.userId()),
                        toDouble(snapshot.getLatestWaistCm()),
                        snapshot.getWaistMeasuredOn()),
                workoutSummary(principal.userId(), snapshot, today),
                new AnalyticsDashboardResponse.NutritionSummary(
                        todayNutrition.valueFor(GoalProgressEvaluator.CALORIES).intValue(),
                        snapshot.getTodayCaloriesTarget(),
                        todayNutrition.valueFor(GoalProgressEvaluator.PROTEIN).doubleValue(),
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
            key = "#principal.userId() + ':' + #period.name() + ':' + #root.target.todayForCacheKey(#principal)")
    public AnalyticsCompareResponse getSummaryCompare(CurrentUserPrincipal principal, ComparePeriod period) {
        LocalDate today = todayForUser(principal.userId());
        PeriodRanges ranges = compareRanges(today, period);
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
                        metricComparison(currentWorkout.totalExerciseCount(), previousWorkout.totalExerciseCount()),
                        new AnalyticsCompareResponse.DaysLogged(
                                currentWorkout.daysWithWorkout(), previousWorkout.daysWithWorkout())),
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
                                previousNutrition.totalFatGrams()),
                        new AnalyticsCompareResponse.DaysLogged(
                                currentNutrition.daysLogged(), previousNutrition.daysLogged()),
                        averagePerLoggedDay(currentNutrition, previousNutrition)),
                today.isBefore(ranges.currentPeriodEnd()));
    }

    private AnalyticsCompareResponse.AveragePerLoggedDay averagePerLoggedDay(
            NutritionSummaryAggregation current, NutritionSummaryAggregation previous) {
        return new AnalyticsCompareResponse.AveragePerLoggedDay(
                averageComparison(BigDecimal.valueOf(current.totalCaloriesConsumed()), current.daysLogged(),
                        BigDecimal.valueOf(previous.totalCaloriesConsumed()), previous.daysLogged(), 0),
                averageComparison(current.totalProteinGrams(), current.daysLogged(),
                        previous.totalProteinGrams(), previous.daysLogged(), 1),
                averageComparison(current.totalCarbsGrams(), current.daysLogged(),
                        previous.totalCarbsGrams(), previous.daysLogged(), 1),
                averageComparison(current.totalFatGrams(), current.daysLogged(),
                        previous.totalFatGrams(), previous.daysLogged(), 1));
    }

    private AnalyticsCompareResponse.MetricComparison averageComparison(BigDecimal currentTotal,
                                                                       int currentDays,
                                                                       BigDecimal previousTotal,
                                                                       int previousDays,
                                                                       int scale) {
        BigDecimal currentAverage = perLoggedDay(currentTotal, currentDays, scale);
        BigDecimal previousAverage = perLoggedDay(previousTotal, previousDays, scale);
        if (currentAverage == null || previousAverage == null) {
            return new AnalyticsCompareResponse.MetricComparison(
                    currentAverage == null ? null : currentAverage.doubleValue(),
                    previousAverage == null ? null : previousAverage.doubleValue(),
                    null,
                    null);
        }
        return metricComparison(currentAverage, previousAverage, false);
    }

    private BigDecimal perLoggedDay(BigDecimal total, int days, int scale) {
        if (days <= 0 || total == null) {
            return null;
        }
        return total.divide(BigDecimal.valueOf(days), scale, RoundingMode.HALF_UP);
    }

    @Override
    @Cacheable(cacheNames = com.fitsync.analytics.config.CacheConfig.GOAL_PROGRESS_CACHE,
            key = "#principal.userId() + ':' + (#status == null ? '' : #status.toUpperCase(T(java.util.Locale).ROOT)) + ':' + #root.target.todayForCacheKey(#principal)")
    public AnalyticsGoalProgressResponse getGoalProgress(CurrentUserPrincipal principal, String status) {
        String normalizedStatus = normalizeStatus(status);
        LocalDate today = todayForUser(principal.userId());
        LiveNutritionDay liveDay = new LiveNutritionDay(principal.userId(), today);
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
                        .map(goal -> {
                            boolean expired = GoalExpiryPolicy.isExpired(goal.getTargetDate(), today);
                            LiveGoalValues live = liveGoalValues(goal, liveDay);
                            return new AnalyticsGoalProgressResponse.GoalProgressView(
                                    goal.getGoalId(),
                                    goal.getGoalType(),
                                    goal.getGoalStatus(),
                                    toDouble(goal.getTargetValue()),
                                    goal.getTargetUnit(),
                                    goal.getTargetDate(),
                                    live.currentValue(),
                                    live.progressRatio(),
                                    goal.getTargetDate() == null
                                            ? goal.getDaysRemaining()
                                            : GoalExpiryPolicy.daysRemaining(goal.getTargetDate(), today),
                                    !expired && live.onTrack(),
                                    expired,
                                    goal.getLastEvaluatedAt());
                        })
                        .toList());
    }

    /** Today's date in the user's stored profile zone (UTC when none is stored). */
    public LocalDate todayForUser(UUID userId) {
        String timezone = dashboardSnapshotRepository.findById(userId)
                .map(AnalyticsUserDashboardSnapshotEntity::getTimezone)
                .orElse(null);
        return LocalDate.now(clock.withZone(UserZone.of(timezone)));
    }

    /** Cache-key part for read models that depend on "today", so entries roll over at the user's midnight. */
    public LocalDate todayForCacheKey(CurrentUserPrincipal principal) {
        return todayForUser(principal.userId());
    }

    /**
     * Daily nutrition goals (calories, protein, carbs, fat) describe one day. The stored goal row
     * is only rewritten when an event arrives, so after midnight it kept showing yesterday's total.
     * They are therefore evaluated again at read time against the row of today in the user's zone,
     * and read 0 when today has no row. The pass/fail rules match {@link GoalProgressEvaluator}:
     * calories, carbs and fat are ceilings and protein is a floor.
     */
    private LiveGoalValues liveGoalValues(AnalyticsUserGoalProgressEntity goal, LiveNutritionDay day) {
        // Goal types are stored as the upper-case enum names the events carry, so they are matched as they are.
        String type = goal.getGoalType() == null ? "" : goal.getGoalType().trim();
        if (!GoalProgressEvaluator.NUTRITION_GOAL_TYPES.contains(type)) {
            return new LiveGoalValues(toDouble(goal.getCurrentValue()), toDouble(goal.getProgressRatio()), goal.isOnTrack());
        }
        BigDecimal current = day.valueFor(type);
        BigDecimal target = goal.getTargetValue();
        BigDecimal ratio = target == null || target.compareTo(BigDecimal.ZERO) <= 0
                ? null
                : current.divide(target, 4, RoundingMode.HALF_UP);
        boolean onTrack = ratio != null && (GoalProgressEvaluator.PROTEIN.equals(type)
                ? current.compareTo(target) >= 0
                : current.compareTo(target) <= 0);
        return new LiveGoalValues(current.doubleValue(), toDouble(ratio), onTrack);
    }

    private final class LiveNutritionDay {
        private final UUID userId;
        private final LocalDate day;
        private AnalyticsUserDailyNutritionEntity row;
        private boolean loaded;

        LiveNutritionDay(UUID userId, LocalDate day) {
            this.userId = userId;
            this.day = day;
        }

        BigDecimal valueFor(String goalType) {
            if (!loaded) {
                row = dailyNutritionRepository.findByUserIdAndActivityDate(userId, day).orElse(null);
                loaded = true;
            }
            if (row == null) {
                return BigDecimal.ZERO;
            }
            BigDecimal value = switch (goalType) {
                case GoalProgressEvaluator.CALORIES -> row.getTotalCaloriesConsumed() == null
                        ? null
                        : BigDecimal.valueOf(row.getTotalCaloriesConsumed());
                case GoalProgressEvaluator.PROTEIN -> row.getTotalProteinGrams();
                case GoalProgressEvaluator.CARBS -> row.getTotalCarbsGrams();
                default -> row.getTotalFatGrams();
            };
            return value == null ? BigDecimal.ZERO : value;
        }
    }

    private record LiveGoalValues(Double currentValue, Double progressRatio, boolean onTrack) {
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
        // Normalized through UserZone so an invalid stored timezone is reported as the UTC
        // fallback actually used to evaluate streak days, not echoed verbatim.
        ZoneId streakZone = UserZone.of(snapshot == null ? null : snapshot.getTimezone());
        String timezone = ZoneOffset.UTC.equals(streakZone) ? DEFAULT_TIMEZONE : streakZone.getId();

        LocalDate lastActiveDate = dailyWorkoutRepository
                .findTopByUserIdAndCompletedWorkoutCountGreaterThanOrderByActivityDateDesc(principal.userId(), 0)
                .map(AnalyticsUserDailyWorkoutEntity::getActivityDate)
                .orElse(null);

        // The stored streak is only rewritten when an event arrives, so reading it back verbatim
        // reports a streak that ended months ago as still running. Re-check it against the clock.
        // See Documents/architecture/functional-defects-2026-09-26.md, defect 3.
        int currentStreakDays = liveStreak(
                snapshot == null ? null : snapshot.getActiveStreakDays(),
                lastActiveDate,
                LocalDate.now(clock.withZone(streakZone)));

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
     * A streak is only "current" while it is still reachable: the last active day must be today or
     * yesterday in the user's own zone. Anything older means the streak has been broken and the
     * stored counter is a historical value, not a live one.
     *
     * <p>Shared by the dashboard and the streaks endpoint on purpose. They previously read the same
     * stored field independently and so agreed on the same wrong answer; sharing the rule means they
     * cannot drift apart.
     */
    private int liveStreak(Integer storedStreakDays, LocalDate lastActiveDate, LocalDate today) {
        int stored = defaultInteger(storedStreakDays);
        if (stored <= 0 || lastActiveDate == null) {
            return 0;
        }
        return lastActiveDate.isBefore(today.minusDays(1)) ? 0 : stored;
    }

    /** Dashboard variant: looks the last active day up, since the snapshot does not carry it. */
    private int liveStreakDays(UUID userId, Integer storedStreakDays, LocalDate today) {
        if (defaultInteger(storedStreakDays) <= 0) {
            return 0;
        }
        LocalDate lastActiveDate = dailyWorkoutRepository
                .findTopByUserIdAndCompletedWorkoutCountGreaterThanOrderByActivityDateDesc(userId, 0)
                .map(AnalyticsUserDailyWorkoutEntity::getActivityDate)
                .orElse(null);
        return liveStreak(storedStreakDays, lastActiveDate, today);
    }

    /**
     * The dashboard carried no measurement date at all, so the client filled the "Latest body
     * metric" caption with the snapshot's generation time instead. See defect 5.
     */
    private LocalDate latestBodyMeasurementDate(UUID userId) {
        return dailyBodyRepository.findTopByUserIdOrderByMetricDateDesc(userId)
                .map(AnalyticsUserDailyBodyEntity::getMetricDate)
                .orElse(null);
    }

    /**
     * Single-week recap. Workout totals reuse the same daily-row aggregation as /workouts/summary
     * and /summary/compare, and the week-over-week deltas compare full ISO weeks (this week's window
     * against the seven days immediately before it), whereas /summary/compare only compares the
     * elapsed part of the week. The default week is anchored to the user's timezone as the
     * weekly-report contract requires. When weekStart is omitted it defaults to the Monday of the
     * current week in the user's timezone; an explicit weekStart must itself be a Monday. A week
     * without data returns zeroed totals and null averages/deltas, never a 404.
     */
    @Override
    @Cacheable(cacheNames = com.fitsync.analytics.config.CacheConfig.WEEKLY_REPORT_CACHE,
            key = "#principal.userId() + ':' + #root.target.weeklyReportCacheWeekStart(#principal, #weekStart)")
    public AnalyticsWeeklyReportResponse getWeeklyReport(CurrentUserPrincipal principal, LocalDate weekStart) {
        if (weekStart != null && weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new BadRequestException("weekStart must be a Monday (ISO week start)");
        }
        LocalDate from = weekStart != null ? weekStart : currentWeekStartInUserZone(principal.userId());
        LocalDate to = from.plusDays(6);

        WorkoutSummaryAggregation currentWorkout = aggregateWorkoutSummary(principal.userId(), from, to);
        WorkoutSummaryAggregation previousWorkout =
                aggregateWorkoutSummary(principal.userId(), from.minusWeeks(1), from.minusDays(1));

        List<AnalyticsUserDailyNutritionEntity> nutritionRows = dailyNutritionRepository
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(principal.userId(), from, to);
        Integer avgDailyCaloriesConsumed = averageDailyCaloriesConsumed(nutritionRows);
        // The macro adherence score is protein eaten over the protein target, so 1 or more means the target was met.
        int proteinTargetHitDays = (int) nutritionRows.stream()
                .map(AnalyticsUserDailyNutritionEntity::getMacroAdherenceScore)
                .filter(Objects::nonNull)
                .filter(score -> score.compareTo(BigDecimal.ONE) >= 0)
                .count();

        return new AnalyticsWeeklyReportResponse(
                from,
                to,
                currentWorkout.totalWorkoutCount(),
                currentWorkout.totalWorkoutMinutes(),
                currentWorkout.totalCaloriesBurned(),
                avgDailyCaloriesConsumed,
                proteinTargetHitDays,
                weeklyWeightDelta(principal.userId(), from, to),
                new AnalyticsWeeklyReportResponse.WeekOverWeekDelta(
                        currentWorkout.totalWorkoutCount() - previousWorkout.totalWorkoutCount(),
                        currentWorkout.totalWorkoutMinutes() - previousWorkout.totalWorkoutMinutes()));
    }

    /**
     * Resolves the effective week for the weekly-report cache key. Public because the
     * {@code @Cacheable} SpEL expression calls it: the default week is the current ISO week in the
     * user's timezone, so the cache entry must roll over at the user's local Monday midnight, not at
     * the UTC date change.
     */
    public LocalDate weeklyReportCacheWeekStart(CurrentUserPrincipal principal, LocalDate weekStart) {
        return weekStart != null ? weekStart : currentWeekStartInUserZone(principal.userId());
    }

    private LocalDate currentWeekStartInUserZone(UUID userId) {
        String timezone = dashboardSnapshotRepository.findById(userId)
                .map(AnalyticsUserDashboardSnapshotEntity::getTimezone)
                .orElse(null);
        return LocalDate.now(clock.withZone(UserZone.of(timezone)))
                .with(WeekFields.ISO.dayOfWeek(), 1);
    }

    /**
     * Averages calories over days that actually have logged meals, so sparse logging does not
     * dilute the average with implicit zero days. Null when the week has no logged days.
     */
    private Integer averageDailyCaloriesConsumed(List<AnalyticsUserDailyNutritionEntity> rows) {
        List<Integer> loggedDayCalories = rows.stream()
                .filter(row -> defaultInteger(row.getMealCount()) > 0)
                .map(row -> defaultInteger(row.getTotalCaloriesConsumed()))
                .toList();
        if (loggedDayCalories.isEmpty()) {
            return null;
        }
        int total = loggedDayCalories.stream().mapToInt(Integer::intValue).sum();
        return BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(loggedDayCalories.size()), 0, RoundingMode.HALF_UP)
                .intValue();
    }

    /**
     * In-week weight change: last weigh-in minus first weigh-in inside the window. Null with fewer
     * than two weigh-ins because a single point has no delta.
     */
    private Double weeklyWeightDelta(UUID userId, LocalDate from, LocalDate to) {
        List<BigDecimal> weights = dailyBodyRepository
                .findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(userId, from, to).stream()
                .map(AnalyticsUserDailyBodyEntity::getWeightKg)
                .filter(Objects::nonNull)
                .toList();
        if (weights.size() < 2) {
            return null;
        }
        return weights.get(weights.size() - 1).subtract(weights.get(0)).doubleValue();
    }

    private List<AnalyticsDashboardResponse.GoalProgressSummary> loadGoalSummaries(
            UUID userId, String status, LocalDate today, LiveNutritionDay liveDay) {
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
                .map(goal -> toGoalProgressSummary(goal, today, liveDay))
                .toList();
    }

    /**
     * Tie-break when several rows share a goal type: the most recently evaluated row wins, and the
     * larger goal id wins when the evaluation times are equal, so the choice is deterministic.
     */
    private boolean shouldReplaceGoalSummary(AnalyticsUserGoalProgressEntity current,
                                             AnalyticsUserGoalProgressEntity candidate) {
        int evaluatedComparison = Comparator.nullsFirst(Instant::compareTo)
                .compare(candidate.getLastEvaluatedAt(), current.getLastEvaluatedAt());
        if (evaluatedComparison != 0) {
            return evaluatedComparison > 0;
        }
        return Comparator.nullsFirst(UUID::compareTo).compare(candidate.getGoalId(), current.getGoalId()) > 0;
    }

    private WorkoutSummaryAggregation aggregateWorkoutSummary(UUID userId, LocalDate from, LocalDate to) {
        List<AnalyticsUserDailyWorkoutEntity> rows = dailyWorkoutRepository
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, from, to);
        return new WorkoutSummaryAggregation(
                rows.stream().mapToInt(row -> defaultInteger(row.getCompletedWorkoutCount())).sum(),
                rows.stream().mapToInt(row -> defaultInteger(row.getTotalWorkoutMinutes())).sum(),
                rows.stream().mapToInt(row -> defaultInteger(row.getTotalCaloriesBurned())).sum(),
                rows.stream().mapToInt(row -> defaultInteger(row.getTotalExerciseCount())).sum(),
                (int) rows.stream().filter(row -> defaultInteger(row.getCompletedWorkoutCount()) > 0).count());
    }

    private NutritionSummaryAggregation aggregateNutritionSummary(UUID userId, LocalDate from, LocalDate to) {
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
                .filter(ratio -> ratio.compareTo(CALORIE_BAND_LOW) >= 0
                        && ratio.compareTo(CALORIE_BAND_HIGH) <= 0)
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
                daysWithinBand,
                (int) rows.stream().filter(row -> defaultInteger(row.getMealCount()) > 0).count());
    }

    /**
     * One definition of "this week" (Monday to Sunday in the user's zone) and of a streak
     * (consecutive days with a completed workout, ending today or yesterday). The three old fields
     * keep their rolling-seven-day meaning for existing clients, and like the new ones they are
     * summed from the daily workout rows at read time rather than copied from the snapshot, whose
     * copies are only as fresh as the last workout event.
     */
    private AnalyticsDashboardResponse.WorkoutSummary workoutSummary(UUID userId,
                                                                    AnalyticsUserDashboardSnapshotEntity snapshot,
                                                                    LocalDate today) {
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        WorkoutSummaryAggregation thisWeek = aggregateWorkoutSummary(userId, monday, monday.plusDays(6));
        // The same seven days, ending today, that the workout projection and the rebuild write.
        List<AnalyticsUserDailyWorkoutEntity> lastSevenDays = dailyWorkoutRepository
                .findByUserIdAndActivityDateBetweenOrderByActivityDateDesc(userId, today.minusDays(6), today);
        int todayWorkoutCount = lastSevenDays.stream()
                .filter(row -> today.equals(row.getActivityDate()))
                .mapToInt(row -> defaultInteger(row.getCompletedWorkoutCount()))
                .sum();
        int rollingWorkoutCount = lastSevenDays.stream()
                .mapToInt(row -> defaultInteger(row.getCompletedWorkoutCount()))
                .sum();
        int rollingWorkoutMinutes = lastSevenDays.stream()
                .mapToInt(row -> defaultInteger(row.getTotalWorkoutMinutes()))
                .sum();
        int streak = liveStreakDays(userId, snapshot.getActiveStreakDays(), today);
        // The old activeStreakDays and the new currentStreakDays carry the same live value.
        return new AnalyticsDashboardResponse.WorkoutSummary(
                todayWorkoutCount,
                rollingWorkoutCount,
                rollingWorkoutMinutes,
                streak,
                thisWeek.totalWorkoutCount(),
                thisWeek.totalWorkoutMinutes(),
                streak);
    }

    private AnalyticsDashboardResponse emptyDashboard(List<AnalyticsDashboardResponse.GoalProgressSummary> goals) {
        return new AnalyticsDashboardResponse(
                Instant.now(clock),
                DEFAULT_TIMEZONE,
                new AnalyticsDashboardResponse.BodySummary(null, null, null, null, null, null, null),
                new AnalyticsDashboardResponse.WorkoutSummary(0, 0, 0, 0, 0, 0, 0),
                new AnalyticsDashboardResponse.NutritionSummary(null, null, null, null),
                goals);
    }

    /**
     * Builds compare windows from today in the user's own zone (UTC when none is stored). WEEK uses ISO-Monday
     * calendar weeks and compares the elapsed part of this week with the same weekday span last week.
     * MONTH compares the elapsed part of this calendar month with the same day-of-month span last
     * month; when the previous month is shorter, the previous end date is clamped to that month's
     * final day. That clamp is intentional and makes month-end comparisons behave as "this month vs
     * last month", even when the window lengths differ.
     */
    private PeriodRanges compareRanges(LocalDate today, ComparePeriod period) {
        return switch (period) {
            case WEEK -> {
                LocalDate currentFrom = today.with(WeekFields.ISO.dayOfWeek(), 1);
                long elapsedDays = ChronoUnit.DAYS.between(currentFrom, today);
                LocalDate previousFrom = currentFrom.minusWeeks(1);
                yield new PeriodRanges(
                        currentFrom, today, previousFrom, previousFrom.plusDays(elapsedDays), currentFrom.plusDays(6));
            }
            case MONTH -> {
                LocalDate currentFrom = today.withDayOfMonth(1);
                LocalDate previousFrom = currentFrom.minusMonths(1);
                int previousEndDay = Math.min(today.getDayOfMonth(), previousFrom.lengthOfMonth());
                yield new PeriodRanges(
                        currentFrom,
                        today,
                        previousFrom,
                        previousFrom.withDayOfMonth(previousEndDay),
                        currentFrom.withDayOfMonth(currentFrom.lengthOfMonth()));
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

    /** Percent change against the previous period; null when the previous period is zero (no baseline). */
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

    /**
     * Expiry is re-evaluated here rather than trusted from the stored row. GoalExpiryPolicy already
     * ran when the goal event was consumed and again in the nightly rollup, but a goal whose target
     * date passes between those points keeps its ACTIVE status and its on-track flag, so the client
     * renders "On track - by 31 Aug 2026" a month after that date. A goal past its deadline is never
     * on track. See Documents/architecture/functional-defects-2026-09-26.md, defect 7.
     */
    private AnalyticsDashboardResponse.GoalProgressSummary toGoalProgressSummary(
            AnalyticsUserGoalProgressEntity entity, LocalDate today, LiveNutritionDay liveDay) {
        boolean expired = GoalExpiryPolicy.isExpired(entity.getTargetDate(), today);
        LiveGoalValues live = liveGoalValues(entity, liveDay);
        return new AnalyticsDashboardResponse.GoalProgressSummary(
                entity.getGoalId(),
                entity.getGoalType(),
                toDouble(entity.getTargetValue()),
                entity.getTargetUnit(),
                entity.getTargetDate(),
                live.currentValue(),
                live.progressRatio(),
                !expired && live.onTrack(),
                expired,
                entity.getLastEvaluatedAt());
    }

    private Double average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP).doubleValue();
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
            LocalDate previousTo,
            LocalDate currentPeriodEnd) {
    }

    private record WorkoutSummaryAggregation(
            int totalWorkoutCount,
            int totalWorkoutMinutes,
            int totalCaloriesBurned,
            int totalExerciseCount,
            int daysWithWorkout) {
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
            int daysWithinBand,
            int daysLogged) {
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
