package com.fitsync.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;

import com.fitsync.analytics.dto.AnalyticsEnergyWeeklyResponse;
import com.fitsync.analytics.dto.ProgressGoalResponse;
import com.fitsync.analytics.dto.ProgressGoalResponse.Body;
import com.fitsync.analytics.dto.ProgressGoalResponse.Compared;
import com.fitsync.analytics.dto.ProgressGoalResponse.DailyWeight;
import com.fitsync.analytics.dto.ProgressGoalResponse.DayState;
import com.fitsync.analytics.dto.ProgressGoalResponse.Goal;
import com.fitsync.analytics.dto.ProgressGoalResponse.Hero;
import com.fitsync.analytics.dto.ProgressGoalResponse.Measure;
import com.fitsync.analytics.dto.ProgressGoalResponse.OtherGoal;
import com.fitsync.analytics.dto.ProgressGoalResponse.Pace;
import com.fitsync.analytics.dto.ProgressGoalResponse.PaceWeek;
import com.fitsync.analytics.dto.ProgressGoalResponse.RateChangeMark;
import com.fitsync.analytics.dto.ProgressGoalResponse.Reading;
import com.fitsync.analytics.dto.ProgressGoalResponse.WeekValue;
import com.fitsync.analytics.dto.ProgressGoalResponse.Weight;
import com.fitsync.analytics.dto.ProgressGoalResponse.WeeklyWeight;
import com.fitsync.analytics.dto.ProgressHighlight;
import com.fitsync.analytics.dto.ProgressPeriod;
import com.fitsync.analytics.entity.AnalyticsUserDailyBodyEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyWorkoutEntity;
import com.fitsync.analytics.entity.AnalyticsUserGoalProgressEntity;
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Goal page of Progress (contract section 2.1), read only from the caller's own projection rows. The
 * weight figures use {@link WeightProjection}, the same arithmetic as user-service's progress insight.
 */
@Service
public class ProgressGoalService {

    static final String MET = "MET";
    static final String MISSED = "MISSED";
    static final String NOT_LOGGED = "NOT_LOGGED";
    static final String FUTURE = "FUTURE";
    /** Calories, carbs and fat count as met within 10% of the target either way; protein from the target up. */
    static final BigDecimal NEAR_TARGET_SHARE = new BigDecimal("0.10");
    /** A difference smaller than this between the 28-day rates before and after a Monday is not marked. */
    static final BigDecimal MIN_RATE_CHANGE = new BigDecimal("0.10");
    private static final List<String> OTHER_GOAL_TYPES = List.of(GoalProgressEvaluator.CALORIES,
            GoalProgressEvaluator.PROTEIN, GoalProgressEvaluator.CARBS, GoalProgressEvaluator.FAT,
            GoalProgressEvaluator.WORKOUT_FREQUENCY);

    private final AnalyticsUserDailyBodyRepository dailyBodyRepository;
    private final AnalyticsUserDailyNutritionRepository dailyNutritionRepository;
    private final AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository;
    private final AnalyticsUserGoalProgressRepository goalProgressRepository;
    private final AnalyticsEnergyService energyService;
    private final Clock clock;

    public ProgressGoalService(AnalyticsUserDailyBodyRepository dailyBodyRepository,
                               AnalyticsUserDailyNutritionRepository dailyNutritionRepository,
                               AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository,
                               AnalyticsUserGoalProgressRepository goalProgressRepository,
                               AnalyticsEnergyService energyService,
                               Clock clock) {
        this.dailyBodyRepository = dailyBodyRepository;
        this.dailyNutritionRepository = dailyNutritionRepository;
        this.dailyWorkoutRepository = dailyWorkoutRepository;
        this.goalProgressRepository = goalProgressRepository;
        this.energyService = energyService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProgressGoalResponse getGoal(UUID userId, ProgressWindow window, String timezone) {
        ZoneId zone = energyService.resolveZone(userId, timezone);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Map<String, AnalyticsUserGoalProgressEntity> goals = activeGoals(userId);
        AnalyticsUserGoalProgressEntity weightGoal = goals.get(GoalProgressEvaluator.WEIGHT);
        BigDecimal goalKg = weightGoal == null ? null : weightGoal.getTargetValue();
        Goal goal = weightGoal == null || goalKg == null ? null
                : new Goal(GoalProgressEvaluator.WEIGHT, goalKg, weightGoal.getTargetDate());

        // Body rows from 28 days before the previous period, so its rate has a full window too.
        LocalDate bodyFrom = window.previousFrom().minusDays(WeightProjection.RATE_WINDOW_DAYS);
        List<AnalyticsUserDailyBodyEntity> bodyRows =
                dailyBodyRepository.findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(userId, bodyFrom, window.to());
        TreeMap<LocalDate, BigDecimal> weights = new TreeMap<>();
        bodyRows.stream().filter(row -> row.getWeightKg() != null)
                .forEach(row -> weights.put(row.getMetricDate(), row.getWeightKg()));
        if (weights.isEmpty()) {
            // The newest weigh-in may be older than every window; it still answers "what do I weigh now".
            dailyBodyRepository
                    .findTopByUserIdAndWeightKgIsNotNullAndMetricDateLessThanEqualOrderByMetricDateDesc(userId, window.to())
                    .ifPresent(row -> weights.put(row.getMetricDate(), row.getWeightKg()));
        }

        LocalDate reference = window.to().isAfter(today) ? today : window.to();
        WeightProjection.Result now = WeightProjection.calculate(weights, reference, goalKg);
        WeightProjection.Result before = WeightProjection.calculate(weights, window.previousTo(), goalKg);

        List<WeeklyWeight> weekly = weeklyWeights(weights, window.from(), window.to());
        List<WeeklyWeight> previousWeekly = weeklyWeights(weights, window.previousFrom(), window.previousTo());
        BigDecimal changeKg = weekly.isEmpty() || previousWeekly.isEmpty() ? null
                : weekly.get(weekly.size() - 1).avgKg().subtract(previousWeekly.get(previousWeekly.size() - 1).avgKg())
                .setScale(2, RoundingMode.HALF_UP);
        Integer daysLeft = goal == null || goal.byDate() == null ? null
                : (int) Math.max(0, ChronoUnit.DAYS.between(today, goal.byDate()));
        Hero hero = new Hero(now.latestKg(), now.measuredOn(), changeKg,
                new Compared<>(now.weeklyRateKg(), before.weeklyRateKg()),
                new Compared<>(now.projectedDate(), before.projectedDate()), daysLeft);

        List<DailyWeight> daily = new ArrayList<>();
        weights.subMap(window.from(), true, window.to(), true)
                .forEach((date, kg) -> daily.add(new DailyWeight(date, kg)));
        List<DailyWeight> projection = now.projectedDate() == null ? List.of()
                : List.of(new DailyWeight(now.measuredOn(), now.latestKg()),
                        new DailyWeight(now.projectedDate(), now.goalKg()));
        Weight weight = new Weight(daily, weekly, projection,
                rateChangeMark(weights, window, reference, goalKg), WeightProjection.METHOD);

        List<AnalyticsUserDailyBodyEntity> periodRows = bodyRows.stream()
                .filter(row -> window.contains(row.getMetricDate())).toList();
        Measure waist = measure(periodRows, AnalyticsUserDailyBodyEntity::getWaistCm, "cm");
        Body body = new Body(measure(periodRows, AnalyticsUserDailyBodyEntity::getBodyFatRate, "points"), waist,
                measure(periodRows, AnalyticsUserDailyBodyEntity::getBmi, ""));

        AnalyticsEnergyWeeklyResponse energy = energyService.getWeekly(userId,
                ProgressWindow.weekStart(reference.isBefore(window.from()) ? window.from() : reference), zone.getId());
        List<PaceWeek> paceWeeks = energy.weeks().stream()
                .map(week -> new PaceWeek(week.weekStart(), week.expectedKgChange(), week.actualKgChange()))
                .toList();

        List<AnalyticsUserDailyNutritionEntity> nutritionRows = dailyNutritionRepository
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, window.from(), window.to());
        Map<LocalDate, AnalyticsUserDailyNutritionEntity> nutrition = new HashMap<>();
        nutritionRows.forEach(row -> nutrition.put(row.getActivityDate(), row));
        List<OtherGoal> otherGoals = otherGoals(userId, goals, window, nutrition, today);

        List<ProgressHighlight> highlights = new ArrayList<>();
        addIfFired(highlights, ProgressHighlightRules.rateInRange(now.weeklyRateKg(), before.weeklyRateKg(),
                now.latestKg(), goalKg));
        addIfFired(highlights, logVsScale(paceWeeks));
        BigDecimal weeklyChange = weekly.size() < 2 ? null
                : weekly.get(weekly.size() - 1).avgKg().subtract(weekly.get(0).avgKg());
        addIfFired(highlights, ProgressHighlightRules.waistVsWeight(
                waist.series().stream().map(Reading::date).toList(),
                waist.series().stream().map(Reading::value).toList(), weeklyChange));
        addIfFired(highlights, proteinMet(goals.get(GoalProgressEvaluator.PROTEIN), nutritionRows, today));

        return new ProgressGoalResponse(new ProgressPeriod(window.from(), window.to()),
                new ProgressPeriod(window.previousFrom(), window.previousTo()), goal, hero, highlights, weight, body,
                new Pace(paceWeeks), otherGoals);
    }

    /** The newest active goal of each type (older read-model rows of the same type are ignored). */
    private Map<String, AnalyticsUserGoalProgressEntity> activeGoals(UUID userId) {
        Map<String, AnalyticsUserGoalProgressEntity> byType = new HashMap<>();
        goalProgressRepository.findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(userId,
                        GoalExpiryPolicy.ACTIVE_STATUS)
                .forEach(goal -> byType.merge(goal.getGoalType(), goal, (kept, other) ->
                        Comparator.comparing(AnalyticsUserGoalProgressEntity::getLastEvaluatedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder())).compare(other, kept) > 0 ? other : kept));
        return byType;
    }

    /** Monday weeks of the dates from {@code from} to {@code to} that hold a weigh-in, averaged to two decimals. */
    static List<WeeklyWeight> weeklyWeights(Map<LocalDate, BigDecimal> weights, LocalDate from, LocalDate to) {
        TreeMap<LocalDate, List<BigDecimal>> byWeek = new TreeMap<>();
        weights.forEach((date, kg) -> {
            if (!date.isBefore(from) && !date.isAfter(to)) {
                byWeek.computeIfAbsent(ProgressWindow.weekStart(date), key -> new ArrayList<>()).add(kg);
            }
        });
        List<WeeklyWeight> weeks = new ArrayList<>();
        byWeek.forEach((monday, values) -> weeks.add(new WeeklyWeight(monday,
                values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP), values.size())));
        return weeks;
    }

    /**
     * For each Monday of the period, the 28-day rate of the weigh-ins before it against the rate of the
     * weigh-ins from that Monday on (up to 28 days, never past the reference date). The mark sits on the
     * Monday where the two differ most, when they differ by at least 0.1 kg a week.
     */
    static RateChangeMark rateChangeMark(TreeMap<LocalDate, BigDecimal> weights, ProgressWindow window,
                                         LocalDate reference, BigDecimal goalKg) {
        BigDecimal bestDelta = null;
        RateChangeMark best = null;
        for (LocalDate monday : window.weekStarts()) {
            if (monday.isBefore(window.from()) || monday.isAfter(reference)) {
                continue;
            }
            BigDecimal before = WeightProjection.calculate(weights, monday.minusDays(1), goalKg).weeklyRateKg();
            LocalDate afterEnd = monday.plusDays(WeightProjection.RATE_WINDOW_DAYS - 1L);
            BigDecimal after = WeightProjection.calculate(weights.tailMap(monday, true),
                    afterEnd.isAfter(reference) ? reference : afterEnd, goalKg).weeklyRateKg();
            if (before == null || after == null) {
                continue;
            }
            BigDecimal delta = after.subtract(before).abs();
            if (delta.compareTo(MIN_RATE_CHANGE) >= 0 && (bestDelta == null || delta.compareTo(bestDelta) > 0)) {
                bestDelta = delta;
                best = new RateChangeMark(monday, markText(before, after, monday), before, after);
            }
        }
        return best;
    }

    private static String markText(BigDecimal before, BigDecimal after, LocalDate monday) {
        String word;
        if (before.signum() != 0 && after.signum() != 0 && before.signum() != after.signum()) {
            word = "Turned";
        } else {
            word = after.abs().compareTo(before.abs()) > 0 ? "Faster" : "Slower";
        }
        return word + " from " + ProgressHighlightRules.day(monday);
    }

    private static Measure measure(List<AnalyticsUserDailyBodyEntity> rows,
                                   Function<AnalyticsUserDailyBodyEntity, BigDecimal> value, String unit) {
        List<Reading> series = new ArrayList<>();
        for (AnalyticsUserDailyBodyEntity row : rows) {
            BigDecimal reading = value.apply(row);
            if (reading != null) {
                series.add(new Reading(row.getMetricDate(), reading));
            }
        }
        if (series.isEmpty()) {
            return new Measure(List.of(), null, null, null);
        }
        BigDecimal start = series.get(0).value();
        BigDecimal end = series.get(series.size() - 1).value();
        String context = null;
        if (series.size() > 1) {
            context = ProgressHighlightRules.signed(end.subtract(start).doubleValue(), 1)
                    + (unit.isEmpty() ? "" : " " + unit) + " since " + ProgressHighlightRules.day(series.get(0).date());
        }
        return new Measure(series, start, end, context);
    }

    private static ProgressHighlight logVsScale(List<PaceWeek> weeks) {
        for (int i = weeks.size() - 1; i >= 0; i--) {
            PaceWeek week = weeks.get(i);
            if (week.expectedKg() != null && week.actualKg() != null) {
                return ProgressHighlightRules.logVsScale(weeks.get(0).weekStart(), week.weekStart(), i + 1,
                        week.expectedKg(), week.actualKg());
            }
        }
        return null;
    }

    private static ProgressHighlight proteinMet(AnalyticsUserGoalProgressEntity proteinGoal,
                                                List<AnalyticsUserDailyNutritionEntity> rows, LocalDate today) {
        List<LocalDate> dates = new ArrayList<>();
        List<BigDecimal> protein = new ArrayList<>();
        int met = 0;
        BigDecimal latestTarget = proteinGoal == null ? null : proteinGoal.getTargetValue();
        for (AnalyticsUserDailyNutritionEntity row : rows) {
            BigDecimal target = row.getProteinTargetGrams() != null ? row.getProteinTargetGrams()
                    : proteinGoal == null ? null : proteinGoal.getTargetValue();
            if (!isLogged(row, today) || target == null || target.signum() <= 0) {
                continue;
            }
            BigDecimal grams = Objects.requireNonNullElse(row.getTotalProteinGrams(), BigDecimal.ZERO);
            dates.add(row.getActivityDate());
            protein.add(grams);
            if (grams.compareTo(target) >= 0) {
                met++;
            }
            latestTarget = target;
        }
        return ProgressHighlightRules.proteinMet(dates, protein, met, latestTarget);
    }

    private List<OtherGoal> otherGoals(UUID userId, Map<String, AnalyticsUserGoalProgressEntity> goals,
                                       ProgressWindow window, Map<LocalDate, AnalyticsUserDailyNutritionEntity> nutrition,
                                       LocalDate today) {
        List<OtherGoal> result = new ArrayList<>();
        for (String type : OTHER_GOAL_TYPES) {
            AnalyticsUserGoalProgressEntity goal = goals.get(type);
            if (goal == null || goal.getTargetValue() == null) {
                continue;
            }
            if (GoalProgressEvaluator.WORKOUT_FREQUENCY.equals(type)) {
                result.add(workoutFrequency(userId, goal, window, today));
            } else {
                result.add(nutritionGoal(type, goal, window, nutrition, today));
            }
        }
        return result;
    }

    static OtherGoal nutritionGoal(String type, AnalyticsUserGoalProgressEntity goal, ProgressWindow window,
                                   Map<LocalDate, AnalyticsUserDailyNutritionEntity> nutrition, LocalDate today) {
        List<DayState> days = new ArrayList<>();
        int met = 0;
        int logged = 0;
        for (LocalDate date = window.from(); !date.isAfter(window.to()); date = date.plusDays(1)) {
            AnalyticsUserDailyNutritionEntity row = nutrition.get(date);
            if (date.isAfter(today)) {
                days.add(new DayState(date, FUTURE));
                continue;
            }
            if (!isLogged(row, today)) {
                days.add(new DayState(date, NOT_LOGGED));
                continue;
            }
            logged++;
            boolean reached = reached(type, row, goal.getTargetValue());
            if (reached) {
                met++;
            }
            days.add(new DayState(date, reached ? MET : MISSED));
        }
        return new OtherGoal(type, goal.getTargetValue(), unit(type, goal.getTargetUnit()), met, logged, days, List.of());
    }

    /** The row's own target for calories and protein when it has one, else the goal's. */
    static boolean reached(String type, AnalyticsUserDailyNutritionEntity row, BigDecimal goalTarget) {
        BigDecimal amount;
        BigDecimal target = goalTarget;
        switch (type) {
            case GoalProgressEvaluator.CALORIES -> {
                amount = BigDecimal.valueOf(Objects.requireNonNullElse(row.getTotalCaloriesConsumed(), 0));
                if (row.getCalorieTarget() != null && row.getCalorieTarget() > 0) {
                    target = BigDecimal.valueOf(row.getCalorieTarget());
                }
            }
            case GoalProgressEvaluator.PROTEIN -> {
                amount = Objects.requireNonNullElse(row.getTotalProteinGrams(), BigDecimal.ZERO);
                if (row.getProteinTargetGrams() != null && row.getProteinTargetGrams().signum() > 0) {
                    target = row.getProteinTargetGrams();
                }
                return amount.compareTo(target) >= 0;
            }
            case GoalProgressEvaluator.CARBS -> amount = Objects.requireNonNullElse(row.getTotalCarbsGrams(), BigDecimal.ZERO);
            default -> amount = Objects.requireNonNullElse(row.getTotalFatGrams(), BigDecimal.ZERO);
        }
        return amount.subtract(target).abs().compareTo(target.multiply(NEAR_TARGET_SHARE)) <= 0;
    }

    private OtherGoal workoutFrequency(UUID userId, AnalyticsUserGoalProgressEntity goal, ProgressWindow window,
                                       LocalDate today) {
        List<LocalDate> mondays = window.weekStarts();
        LocalDate first = mondays.get(0);
        LocalDate last = mondays.get(mondays.size() - 1).plusDays(6);
        Map<LocalDate, Integer> sessions = new HashMap<>();
        for (AnalyticsUserDailyWorkoutEntity row : dailyWorkoutRepository
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, first, last)) {
            sessions.put(row.getActivityDate(), Objects.requireNonNullElse(row.getCompletedWorkoutCount(), 0));
        }
        return workoutFrequency(goal, window, sessions, today);
    }

    static OtherGoal workoutFrequency(AnalyticsUserGoalProgressEntity goal, ProgressWindow window,
                                      Map<LocalDate, Integer> sessions, LocalDate today) {
        BigDecimal target = goal.getTargetValue();
        List<WeekValue> weeks = new ArrayList<>();
        int metWeeks = 0;
        int countedWeeks = 0;
        for (LocalDate monday : window.weekStarts()) {
            int count = 0;
            for (int d = 0; d < 7; d++) {
                count += sessions.getOrDefault(monday.plusDays(d), 0);
            }
            weeks.add(new WeekValue(monday, BigDecimal.valueOf(count), target));
            if (monday.isAfter(today)) {
                continue;
            }
            boolean finished = monday.plusDays(6).isBefore(today);
            boolean met = BigDecimal.valueOf(count).compareTo(target) >= 0;
            if (finished || met) {
                countedWeeks++;
                if (met) {
                    metWeeks++;
                }
            }
        }
        List<DayState> days = new ArrayList<>();
        for (LocalDate date = window.from(); !date.isAfter(window.to()); date = date.plusDays(1)) {
            String state = date.isAfter(today) ? FUTURE : sessions.getOrDefault(date, 0) > 0 ? MET : NOT_LOGGED;
            days.add(new DayState(date, state));
        }
        return new OtherGoal(GoalProgressEvaluator.WORKOUT_FREQUENCY, target,
                unit(GoalProgressEvaluator.WORKOUT_FREQUENCY, goal.getTargetUnit()), metWeeks, countedWeeks, days, weeks);
    }

    static boolean isLogged(AnalyticsUserDailyNutritionEntity row, LocalDate today) {
        return row != null && !row.getActivityDate().isAfter(today)
                && Objects.requireNonNullElse(row.getMealCount(), 0) > 0;
    }

    private static String unit(String type, String stored) {
        if (stored != null && !stored.isBlank()) {
            return stored;
        }
        return switch (type) {
            case GoalProgressEvaluator.CALORIES -> "kcal";
            case GoalProgressEvaluator.WORKOUT_FREQUENCY -> "sessions";
            default -> "g";
        };
    }

    private static void addIfFired(List<ProgressHighlight> highlights, ProgressHighlight highlight) {
        if (highlight != null) {
            highlights.add(highlight);
        }
    }
}
