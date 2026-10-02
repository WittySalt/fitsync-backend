package com.fitsync.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import com.fitsync.analytics.dto.AnalyticsEnergyWeeklyResponse;
import com.fitsync.analytics.dto.AnalyticsEnergyWeeklyResponse.Average;
import com.fitsync.analytics.dto.AnalyticsEnergyWeeklyResponse.Day;
import com.fitsync.analytics.dto.AnalyticsEnergyWeeklyResponse.MealSplit;
import com.fitsync.analytics.dto.AnalyticsEnergyWeeklyResponse.Week;
import com.fitsync.analytics.entity.AnalyticsMealContributionEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyWorkoutEntity;
import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import com.fitsync.analytics.entity.AnalyticsUserEnergyProfileEntity;
import com.fitsync.analytics.repository.AnalyticsMealContributionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserEnergyProfileRepository;
import com.fitsync.web.error.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Energy this week" (contract section 11.2). Everything comes from projections that events already
 * filled: daily nutrition and workout rows, meal contributions, weigh-ins and the history of
 * computed energy profiles. Only the caller's own rows are read.
 */
@Service
public class AnalyticsEnergyService {

    static final double RESTING_FACTOR = 1.2;
    static final int MIN_LOGGED_DAYS = 3;
    static final int WEEKS_SHOWN = 8;
    private static final BigDecimal KCAL_PER_KG = BigDecimal.valueOf(7700);

    static final String STATUS_OK = "OK";
    static final String STATUS_NO_PROFILE = "NO_PROFILE";
    static final String STATUS_TOO_FEW_DAYS = "TOO_FEW_DAYS";

    /** Meal type as sent on the event to its group in the split; the lookup ignores case. */
    private static final Map<String, String> MEAL_GROUPS = mealGroups();

    private static Map<String, String> mealGroups() {
        Map<String, String> groups = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        groups.put("BREAKFAST", "breakfast");
        groups.put("LUNCH", "lunch");
        groups.put("DINNER", "dinner");
        groups.put("SUPPER", "dinner");
        groups.put("SNACK", "snack");
        return groups;
    }

    private final AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository;
    private final AnalyticsUserDailyNutritionRepository dailyNutritionRepository;
    private final AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository;
    private final AnalyticsUserDailyBodyRepository dailyBodyRepository;
    private final AnalyticsMealContributionRepository mealContributionRepository;
    private final AnalyticsUserEnergyProfileRepository energyProfileRepository;
    private final Clock clock;

    public AnalyticsEnergyService(AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository,
                                  AnalyticsUserDailyNutritionRepository dailyNutritionRepository,
                                  AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository,
                                  AnalyticsUserDailyBodyRepository dailyBodyRepository,
                                  AnalyticsMealContributionRepository mealContributionRepository,
                                  AnalyticsUserEnergyProfileRepository energyProfileRepository,
                                  Clock clock) {
        this.dashboardSnapshotRepository = dashboardSnapshotRepository;
        this.dailyNutritionRepository = dailyNutritionRepository;
        this.dailyWorkoutRepository = dailyWorkoutRepository;
        this.dailyBodyRepository = dailyBodyRepository;
        this.mealContributionRepository = mealContributionRepository;
        this.energyProfileRepository = energyProfileRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AnalyticsEnergyWeeklyResponse getWeekly(UUID userId, LocalDate weekStart, String timezone) {
        if (weekStart != null && weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new BadRequestException("weekStart must be a Monday (ISO week start)");
        }
        ZoneId zone = resolveRequestedZone(userId, timezone);
        LocalDate start = weekStart != null
                ? weekStart
                : LocalDate.now(clock.withZone(zone)).with(WeekFields.ISO.dayOfWeek(), 1);
        LocalDate end = start.plusDays(6);
        LocalDate rangeStart = start.minusWeeks(WEEKS_SHOWN - 1L);

        Map<LocalDate, AnalyticsUserDailyNutritionEntity> nutrition = new HashMap<>();
        dailyNutritionRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, rangeStart, end)
                .forEach(row -> nutrition.put(row.getActivityDate(), row));
        Map<LocalDate, AnalyticsUserDailyWorkoutEntity> workouts = new HashMap<>();
        dailyWorkoutRepository.findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, rangeStart, end)
                .forEach(row -> workouts.put(row.getActivityDate(), row));
        BmrTimeline timeline = loadTimeline(userId, rangeStart, end, zone);
        LocalDate today = LocalDate.now(clock.withZone(zone));

        List<Day> days = new ArrayList<>();
        int runningTotal = 0;
        int loggedDays = 0;
        long eatenSum = 0;
        long restingSum = 0;
        long workoutSum = 0;
        boolean allLoggedHaveBmr = true;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            AnalyticsUserEnergyProfileEntity profile = timeline.forDay(date);
            Integer resting = profile == null ? null : restingKcal(profile.getBmrKcal());
            AnalyticsUserDailyWorkoutEntity workoutRow = workouts.get(date);
            int workoutKcal = workoutRow == null ? 0 : defaultInt(workoutRow.getTotalCaloriesBurned());
            boolean estimated = workoutRow != null && defaultInt(workoutRow.getEstimatedCaloriesBurned()) > 0;
            AnalyticsUserDailyNutritionEntity nutritionRow = nutrition.get(date);
            boolean logged = nutritionRow != null && defaultInt(nutritionRow.getMealCount()) > 0;
            if (!logged) {
                days.add(new Day(date, false, date.equals(today), null, resting, workoutKcal, estimated, null, null));
                continue;
            }
            if (date.equals(today)) {
                // Today is still going: shown as far as it is logged, never counted.
                days.add(new Day(date, true, true, defaultInt(nutritionRow.getTotalCaloriesConsumed()), resting,
                        workoutKcal, estimated, null, null));
                continue;
            }
            int eaten = defaultInt(nutritionRow.getTotalCaloriesConsumed());
            Integer balance = resting == null ? null : eaten - (resting + workoutKcal);
            if (balance != null) {
                runningTotal += balance;
            }
            loggedDays++;
            eatenSum += eaten;
            workoutSum += workoutKcal;
            if (resting == null) {
                allLoggedHaveBmr = false;
            } else {
                restingSum += resting;
            }
            days.add(new Day(date, true, false, eaten, resting, workoutKcal, estimated, balance,
                    balance == null ? null : runningTotal));
        }

        AnalyticsUserEnergyProfileEntity weekProfile = timeline.forDay(end);
        String status = weekProfile == null ? STATUS_NO_PROFILE
                : loggedDays < MIN_LOGGED_DAYS ? STATUS_TOO_FEW_DAYS : STATUS_OK;

        Average average = null;
        MealSplit split = null;
        Double kg = null;
        if (loggedDays > 0) {
            int avgEaten = average(eatenSum, loggedDays);
            int avgWorkouts = average(workoutSum, loggedDays);
            Integer avgResting = allLoggedHaveBmr ? average(restingSum, loggedDays) : null;
            Integer avgUsed = avgResting == null ? null : avgResting + avgWorkouts;
            Integer avgBalance = avgUsed == null ? null : avgEaten - avgUsed;
            average = new Average(avgEaten, avgResting, avgWorkouts, avgUsed, avgBalance);
            split = mealSplit(userId, days, start, end, loggedDays);
            if (STATUS_OK.equals(status) && avgBalance != null) {
                kg = BigDecimal.valueOf(avgBalance).multiply(BigDecimal.valueOf(7))
                        .divide(KCAL_PER_KG, 2, RoundingMode.HALF_UP).doubleValue();
            }
        }

        return new AnalyticsEnergyWeeklyResponse(
                start, end, status, loggedDays,
                weekProfile == null ? null : weekProfile.getBmrKcal(),
                weekProfile != null && weekProfile.isEstimated(),
                RESTING_FACTOR, average, split, kg, days,
                weeks(userId, rangeStart, nutrition, workouts, timeline, today));
    }

    /** Whole-kcal split of the logged days' meals by type, averaged per logged day. */
    private MealSplit mealSplit(UUID userId, List<Day> days, LocalDate start, LocalDate end, int loggedDays) {
        Set<LocalDate> logged = new HashSet<>();
        days.stream().filter(day -> day.logged() && !day.today()).forEach(day -> logged.add(day.date()));
        long breakfast = 0;
        long lunch = 0;
        long dinner = 0;
        long snack = 0;
        long other = 0;
        for (AnalyticsMealContributionEntity meal
                : mealContributionRepository.findByUserIdAndActivityDateBetween(userId, start, end)) {
            if (!logged.contains(meal.getActivityDate())) {
                continue;
            }
            int kcal = defaultInt(meal.getTotalCalories());
            switch (mealGroup(meal.getMealType())) {
                case "breakfast" -> breakfast += kcal;
                case "lunch" -> lunch += kcal;
                case "dinner" -> dinner += kcal;
                case "snack" -> snack += kcal;
                default -> other += kcal;
            }
        }
        return new MealSplit(average(breakfast, loggedDays), average(lunch, loggedDays),
                average(dinner, loggedDays), average(snack, loggedDays), average(other, loggedDays));
    }

    static String mealGroup(String mealType) {
        if (mealType == null) {
            return "other";
        }
        return MEAL_GROUPS.getOrDefault(mealType.trim(), "other");
    }

    private List<Week> weeks(UUID userId, LocalDate rangeStart,
                             Map<LocalDate, AnalyticsUserDailyNutritionEntity> nutrition,
                             Map<LocalDate, AnalyticsUserDailyWorkoutEntity> workouts,
                             BmrTimeline timeline, LocalDate today) {
        LocalDate rangeEnd = rangeStart.plusWeeks(WEEKS_SHOWN).minusDays(1);
        Map<LocalDate, BigDecimal> weights = new HashMap<>();
        dailyBodyRepository.findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(userId, rangeStart, rangeEnd)
                .stream()
                .filter(row -> row.getWeightKg() != null)
                .forEach(row -> weights.put(row.getMetricDate(), row.getWeightKg()));

        List<Week> weeks = new ArrayList<>();
        long cumulativeBalance = 0;
        boolean anyBalance = false;
        BigDecimal baseline = null;
        for (int i = 0; i < WEEKS_SHOWN; i++) {
            LocalDate weekStart = rangeStart.plusWeeks(i);
            BigDecimal weightSum = BigDecimal.ZERO;
            int weightCount = 0;
            for (int d = 0; d < 7; d++) {
                LocalDate date = weekStart.plusDays(d);
                BigDecimal weight = weights.get(date);
                if (weight != null) {
                    weightSum = weightSum.add(weight);
                    weightCount++;
                }
                AnalyticsUserDailyNutritionEntity row = nutrition.get(date);
                if (row == null || defaultInt(row.getMealCount()) <= 0 || date.equals(today)) {
                    continue;
                }
                AnalyticsUserEnergyProfileEntity profile = timeline.forDay(date);
                if (profile == null) {
                    continue;
                }
                AnalyticsUserDailyWorkoutEntity workout = workouts.get(date);
                int used = restingKcal(profile.getBmrKcal())
                        + (workout == null ? 0 : defaultInt(workout.getTotalCaloriesBurned()));
                cumulativeBalance += defaultInt(row.getTotalCaloriesConsumed()) - used;
                anyBalance = true;
            }
            Double expected = anyBalance
                    ? BigDecimal.valueOf(cumulativeBalance).divide(KCAL_PER_KG, 2, RoundingMode.HALF_UP).doubleValue()
                    : null;
            Double actual = null;
            if (weightCount > 0) {
                BigDecimal average = weightSum.divide(BigDecimal.valueOf(weightCount), 4, RoundingMode.HALF_UP);
                if (baseline == null) {
                    // The first week with a weigh-in is the starting point; weeks before it have no baseline.
                    baseline = average;
                }
                actual = average.subtract(baseline).setScale(2, RoundingMode.HALF_UP).doubleValue();
            }
            weeks.add(new Week(weekStart, expected, actual));
        }
        return weeks;
    }

    private BmrTimeline loadTimeline(UUID userId, LocalDate rangeStart, LocalDate rangeEnd, ZoneId zone) {
        Instant from = rangeStart.atStartOfDay(zone).toInstant();
        Instant to = rangeEnd.plusDays(1).atStartOfDay(zone).toInstant();
        AnalyticsUserEnergyProfileEntity preceding = energyProfileRepository
                .findFirstByUserIdAndComputedAtBeforeOrderByComputedAtDesc(userId, from).orElse(null);
        List<AnalyticsUserEnergyProfileEntity> inRange = energyProfileRepository
                .findByUserIdAndComputedAtGreaterThanEqualAndComputedAtBeforeOrderByComputedAtAsc(userId, from, to);
        List<AnalyticsUserEnergyProfileEntity> rows = new ArrayList<>();
        if (preceding != null) {
            rows.add(preceding);
        }
        rows.addAll(inRange);
        AnalyticsUserEnergyProfileEntity fallback = !rows.isEmpty()
                ? rows.get(0)
                : energyProfileRepository.findFirstByUserIdOrderByComputedAtAsc(userId).orElse(null);
        return new BmrTimeline(rows, fallback, zone);
    }

    /** The BMR of a day is the newest computation before the next midnight, else the earliest one. */
    private record BmrTimeline(List<AnalyticsUserEnergyProfileEntity> rowsOldestFirst,
                               AnalyticsUserEnergyProfileEntity fallback,
                               ZoneId zone) {

        AnalyticsUserEnergyProfileEntity forDay(LocalDate day) {
            Instant dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant();
            AnalyticsUserEnergyProfileEntity chosen = null;
            for (AnalyticsUserEnergyProfileEntity row : rowsOldestFirst) {
                if (row.getComputedAt().isBefore(dayEnd)) {
                    chosen = row;
                } else {
                    break;
                }
            }
            return chosen != null ? chosen : fallback;
        }
    }

    private ZoneId resolveRequestedZone(UUID userId, String timezone) {
        if (timezone != null && !timezone.isBlank()) {
            try {
                return ZoneId.of(timezone.trim());
            } catch (DateTimeException ex) {
                throw new BadRequestException("timezone must be an IANA time zone such as Asia/Singapore");
            }
        }
        String stored = dashboardSnapshotRepository.findById(userId)
                .map(AnalyticsUserDashboardSnapshotEntity::getTimezone)
                .orElse(null);
        if (stored != null && !stored.isBlank()) {
            try {
                return ZoneId.of(stored.trim());
            } catch (DateTimeException ex) {
                return ZoneOffset.UTC;
            }
        }
        return ZoneOffset.UTC;
    }

    private static int restingKcal(Integer bmrKcal) {
        return BigDecimal.valueOf(bmrKcal).multiply(BigDecimal.valueOf(RESTING_FACTOR))
                .setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private static int average(long sum, int count) {
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP).intValue();
    }

    private static int defaultInt(Integer value) {
        return Objects.requireNonNullElse(value, 0);
    }
}
