package com.fitsync.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;

import com.fitsync.analytics.dto.AnalyticsEnergyWeeklyResponse;
import com.fitsync.analytics.dto.ProgressFoodResponse;
import com.fitsync.analytics.dto.ProgressFoodResponse.Balance;
import com.fitsync.analytics.dto.ProgressFoodResponse.Compared;
import com.fitsync.analytics.dto.ProgressFoodResponse.CurrentWeek;
import com.fitsync.analytics.dto.ProgressFoodResponse.DaysLogged;
import com.fitsync.analytics.dto.ProgressFoodResponse.Energy;
import com.fitsync.analytics.dto.ProgressFoodResponse.Habits;
import com.fitsync.analytics.dto.ProgressFoodResponse.Hero;
import com.fitsync.analytics.dto.ProgressFoodResponse.Macro;
import com.fitsync.analytics.dto.ProgressFoodResponse.Macros;
import com.fitsync.analytics.dto.ProgressFoodResponse.MealShare;
import com.fitsync.analytics.dto.ProgressFoodResponse.Meals;
import com.fitsync.analytics.dto.ProgressFoodResponse.MonthDays;
import com.fitsync.analytics.dto.ProgressFoodResponse.Plate;
import com.fitsync.analytics.dto.ProgressFoodResponse.PlateChange;
import com.fitsync.analytics.dto.ProgressFoodResponse.PlateGroup;
import com.fitsync.analytics.dto.ProgressFoodResponse.SummaryLine;
import com.fitsync.analytics.dto.ProgressFoodResponse.WeekBalance;
import com.fitsync.analytics.dto.ProgressFoodResponse.WeekValue;
import com.fitsync.analytics.dto.ProgressHighlight;
import com.fitsync.analytics.dto.ProgressPeriod;
import com.fitsync.analytics.entity.AnalyticsMealContributionEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;
import com.fitsync.analytics.entity.AnalyticsUserGoalProgressEntity;
import com.fitsync.analytics.repository.AnalyticsMealContributionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import com.fitsync.web.error.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Food page of Progress, the part analytics knows (contract section 2.2). Food comes from the daily
 * nutrition rows and the meal contributions; energy reuses {@link AnalyticsEnergyService}. Only the caller's
 * own rows are read, each table once for the period and the period before.
 */
@Service
public class ProgressFoodService {

    static final String BASIS_CALORIES = "CALORIES";
    static final String DRINKS = "DRINKS";
    static final List<String> PLATE_ORDER =
            List.of("VEG", "FRUIT", "GRAINS", "PROTEIN", "DAIRY", "NUTS", "LEGUMES", "SWEETS", "OTHER");
    static final List<String> MEAL_ORDER = List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK", "OTHER");
    static final String UP = "UP";
    static final String STEADY = "STEADY";
    static final String DOWN = "DOWN";
    static final String GRANULARITY_WEEK = "week";
    static final String GRANULARITY_MONTH = "month";
    static final String SO_FAR = "so far";
    private static final int PLATE_CHANGES = 3;

    private final AnalyticsUserDailyNutritionRepository dailyNutritionRepository;
    private final AnalyticsMealContributionRepository mealContributionRepository;
    private final AnalyticsUserGoalProgressRepository goalProgressRepository;
    private final AnalyticsEnergyService energyService;
    private final Clock clock;

    public ProgressFoodService(AnalyticsUserDailyNutritionRepository dailyNutritionRepository,
                               AnalyticsMealContributionRepository mealContributionRepository,
                               AnalyticsUserGoalProgressRepository goalProgressRepository,
                               AnalyticsEnergyService energyService,
                               Clock clock) {
        this.dailyNutritionRepository = dailyNutritionRepository;
        this.mealContributionRepository = mealContributionRepository;
        this.goalProgressRepository = goalProgressRepository;
        this.energyService = energyService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProgressFoodResponse getFood(UUID userId, ProgressWindow window, String timezone) {
        return getFood(userId, window, timezone, GRANULARITY_WEEK);
    }

    /**
     * {@code granularity} is "week" (one point per Monday week) or "month" (one point per calendar month: the
     * months the period covers whole, plus the month that holds today as "so far"); anything else is a 400.
     */
    @Transactional(readOnly = true)
    public ProgressFoodResponse getFood(UUID userId, ProgressWindow window, String timezone, String granularity) {
        boolean monthly = isMonthly(granularity);
        ZoneId zone = energyService.resolveZone(userId, timezone);
        LocalDate today = LocalDate.now(clock.withZone(zone));

        List<AnalyticsUserDailyNutritionEntity> rows = dailyNutritionRepository
                .findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(userId, window.previousFrom(), window.to());
        // Today is still going: it is reported "so far" in currentWeek and never enters a figure below.
        List<AnalyticsUserDailyNutritionEntity> current = rows.stream()
                .filter(row -> window.contains(row.getActivityDate()) && isFullDay(row, today))
                .toList();
        List<AnalyticsUserDailyNutritionEntity> previous = rows.stream()
                .filter(row -> window.containsPrevious(row.getActivityDate()) && isFullDay(row, today))
                .toList();
        LocalDate thisMonday = ProgressWindow.weekStart(today);
        List<AnalyticsUserDailyNutritionEntity> thisWeekDays = current.stream()
                .filter(row -> !row.getActivityDate().isBefore(thisMonday)).toList();
        boolean currentWeekIncluded = thisWeekDays.size() >= ProgressWindow.MIN_FULL_DAYS_FOR_CURRENT_WEEK;

        AnalyticsEnergyWeeklyResponse thisWeek = energyService.getWeekly(userId, null, zone.getId());
        List<MonthBucket> months = monthly ? months(window, today) : List.of();
        List<WeekBalance> balances = monthly ? monthlyBalances(userId, months, zone)
                : weeklyBalances(userId, window, today, zone);

        Map<String, AnalyticsUserGoalProgressEntity> goals = activeGoals(userId);
        Integer eaten = averageKcal(current);
        Hero hero = new Hero(
                new Compared<>(eaten, averageKcal(previous)),
                new Compared<>(averageGrams(current, AnalyticsUserDailyNutritionEntity::getTotalProteinGrams),
                        averageGrams(previous, AnalyticsUserDailyNutritionEntity::getTotalProteinGrams)),
                new Balance(thisWeek.average() == null ? null : thisWeek.average().balance(), thisWeek.daysLogged()),
                new DaysLogged(current.size(), previous.size(), window.days()),
                calorieTarget(goals.get(GoalProgressEvaluator.CALORIES), current));
        CurrentWeek currentWeek = currentWeek(window, today, rows, thisMonday, thisWeekDays, currentWeekIncluded);

        BigDecimal proteinTarget = target(goals.get(GoalProgressEvaluator.PROTEIN), current);
        List<AnalyticsUserDailyNutritionEntity> weekly = currentWeekIncluded ? current
                : current.stream().filter(row -> row.getActivityDate().isBefore(thisMonday)).toList();
        Macro protein;
        Macro carbs;
        Macro fat;
        if (monthly) {
            protein = monthMacro(months, current, AnalyticsUserDailyNutritionEntity::getTotalProteinGrams, proteinTarget);
            carbs = monthMacro(months, current, AnalyticsUserDailyNutritionEntity::getTotalCarbsGrams,
                    goalTarget(goals.get(GoalProgressEvaluator.CARBS)));
            fat = monthMacro(months, current, AnalyticsUserDailyNutritionEntity::getTotalFatGrams,
                    goalTarget(goals.get(GoalProgressEvaluator.FAT)));
        } else {
            protein = macro(window, weekly, AnalyticsUserDailyNutritionEntity::getTotalProteinGrams, proteinTarget);
            carbs = macro(window, weekly, AnalyticsUserDailyNutritionEntity::getTotalCarbsGrams,
                    goalTarget(goals.get(GoalProgressEvaluator.CARBS)));
            fat = macro(window, weekly, AnalyticsUserDailyNutritionEntity::getTotalFatGrams,
                    goalTarget(goals.get(GoalProgressEvaluator.FAT)));
        }
        List<SummaryLine> summary = new ArrayList<>();
        addLine(summary, "Protein", protein);
        addLine(summary, "Carbs", carbs);
        addLine(summary, "Fat", fat);

        List<PlateGroup> plateNow = plate(current);
        List<PlateGroup> plateBefore = plate(previous);
        List<PlateChange> changes = plateChanges(plateNow, plateBefore);
        Plate plate = new Plate(BASIS_CALORIES, plateNow, plateBefore, changes);

        List<AnalyticsMealContributionEntity> meals = mealContributionRepository
                .findByUserIdAndActivityDateBetween(userId, window.previousFrom(), window.to());
        List<MealShare> shares = mealShares(meals, current, previous);

        List<ProgressHighlight> highlights = new ArrayList<>();
        if (!monthly) {
            addIfFired(highlights, ProgressHighlightRules.proteinDrift(
                    protein.series().stream().map(WeekValue::weekStart).toList(),
                    protein.series().stream().map(WeekValue::value).toList(), proteinTarget));
        }
        if (!changes.isEmpty()) {
            String group = changes.get(0).group();
            addIfFired(highlights, ProgressHighlightRules.plateShift(group, share(plateBefore, group),
                    share(plateNow, group)));
        }
        addIfFired(highlights, largestMeal(shares, eaten));

        return new ProgressFoodResponse(new ProgressPeriod(window.from(), window.to()),
                new ProgressPeriod(window.previousFrom(), window.previousTo()), hero, highlights,
                new Energy(thisWeek, balances), new Macros(protein, carbs, fat, summary), plate,
                new Meals(shares, previousMealShares(meals, previous)),
                new Habits(current.size(), monthly ? loggedDaysPerMonth(months, current) : List.of()), currentWeek,
                monthly ? GRANULARITY_MONTH : GRANULARITY_WEEK);
    }

    static boolean isMonthly(String granularity) {
        if (granularity == null || GRANULARITY_WEEK.equals(granularity)) {
            return false;
        }
        if (GRANULARITY_MONTH.equals(granularity)) {
            return true;
        }
        throw new BadRequestException("granularity must be week or month");
    }

    private List<WeekBalance> weeklyBalances(UUID userId, ProgressWindow window, LocalDate today, ZoneId zone) {
        LocalDate lastMonday = ProgressWindow.weekStart(window.to().isAfter(today) ? today : window.to());
        return energyService.weeklyBalances(userId, ProgressWindow.weekStart(window.from()), lastMonday, zone).stream()
                .filter(week -> !week.current() || week.daysLogged() >= ProgressWindow.MIN_FULL_DAYS_FOR_CURRENT_WEEK)
                .map(week -> new WeekBalance(week.weekStart(), week.balance(), week.daysLogged(), week.current()))
                .toList();
    }

    private List<WeekBalance> monthlyBalances(UUID userId, List<MonthBucket> months, ZoneId zone) {
        if (months.isEmpty()) {
            return List.of();
        }
        Map<LocalDate, MonthBucket> byStart = new HashMap<>();
        months.forEach(month -> byStart.put(month.start(), month));
        List<WeekBalance> bars = new ArrayList<>();
        for (AnalyticsEnergyService.WeekBalance bar : energyService.monthlyBalances(userId, months.get(0).start(),
                months.get(months.size() - 1).start(), zone)) {
            MonthBucket month = byStart.get(bar.weekStart());
            if (month == null || month.current() && bar.daysLogged() < ProgressWindow.MIN_FULL_DAYS_FOR_CURRENT_WEEK) {
                continue;
            }
            bars.add(new WeekBalance(bar.weekStart(), bar.balance(), bar.daysLogged(), month.current(), month.label(),
                    month.current(), month.current() ? SO_FAR : null));
        }
        return bars;
    }

    /**
     * A calendar month of a monthly series: a month the period covers whole, or the month that holds today
     * when the period reaches today ({@code current}, shown as "so far").
     */
    record MonthBucket(LocalDate start, LocalDate end, boolean current) {

        String label() {
            return YearMonth.from(start).toString();
        }

        boolean contains(LocalDate date) {
            return !date.isBefore(start) && !date.isAfter(end);
        }
    }

    static List<MonthBucket> months(ProgressWindow window, LocalDate today) {
        List<MonthBucket> buckets = new ArrayList<>();
        for (LocalDate start = window.from().withDayOfMonth(1); !start.isAfter(window.to());
             start = start.plusMonths(1)) {
            LocalDate end = start.plusMonths(1).minusDays(1);
            boolean startsInside = !start.isBefore(window.from());
            boolean holdsToday = !today.isBefore(start) && !today.isAfter(end);
            boolean whole = startsInside && !end.isAfter(window.to());
            if (whole || startsInside && holdsToday && window.contains(today)) {
                buckets.add(new MonthBucket(start, end, holdsToday));
            }
        }
        return buckets;
    }

    /**
     * Grams a day over the logged days of each calendar month; a month without a logged day stays in the
     * series as a point with a null value. The month that holds today enters with two logged days before
     * today, as the current week does.
     */
    static Macro monthMacro(List<MonthBucket> months, List<AnalyticsUserDailyNutritionEntity> logged,
                            Function<AnalyticsUserDailyNutritionEntity, BigDecimal> grams, BigDecimal target) {
        List<WeekValue> series = new ArrayList<>();
        for (MonthBucket month : months) {
            List<AnalyticsUserDailyNutritionEntity> days = logged.stream()
                    .filter(row -> month.contains(row.getActivityDate())).toList();
            if (month.current() && days.size() < ProgressWindow.MIN_FULL_DAYS_FOR_CURRENT_WEEK) {
                continue;
            }
            series.add(new WeekValue(month.start(), averageGrams(days, grams), month.label(), month.current(),
                    month.current() ? SO_FAR : null));
        }
        BigDecimal first = series.stream().map(WeekValue::value).filter(Objects::nonNull).findFirst().orElse(null);
        BigDecimal last = series.stream().map(WeekValue::value).filter(Objects::nonNull).reduce((a, b) -> b)
                .orElse(null);
        return new Macro(first, last, target, series);
    }

    private static List<MonthDays> loggedDaysPerMonth(List<MonthBucket> months,
                                                      List<AnalyticsUserDailyNutritionEntity> logged) {
        return months.stream().map(month -> new MonthDays(month.label(),
                (int) logged.stream().filter(row -> month.contains(row.getActivityDate())).count())).toList();
    }

    /** The previous period's meal shares in the same shape, null when it holds no meals. */
    static List<MealShare> previousMealShares(List<AnalyticsMealContributionEntity> meals,
                                              List<AnalyticsUserDailyNutritionEntity> previous) {
        Set<LocalDate> days = new HashSet<>();
        previous.forEach(row -> days.add(row.getActivityDate()));
        Map<String, Long> before = new LinkedHashMap<>();
        MEAL_ORDER.forEach(type -> before.put(type, 0L));
        for (AnalyticsMealContributionEntity meal : meals) {
            if (days.contains(meal.getActivityDate())) {
                before.merge(mealType(AnalyticsEnergyService.mealGroup(meal.getMealType())),
                        (long) Objects.requireNonNullElse(meal.getTotalCalories(), 0), Long::sum);
            }
        }
        if (before.values().stream().mapToLong(Long::longValue).sum() <= 0) {
            return null;
        }
        int[] pct = largestRemainder(List.copyOf(before.values()), 100);
        List<MealShare> shares = new ArrayList<>();
        for (int i = 0; i < MEAL_ORDER.size(); i++) {
            String type = MEAL_ORDER.get(i);
            if ("OTHER".equals(type) && before.get(type) == 0) {
                continue;
            }
            int kcalPerDay = BigDecimal.valueOf(before.get(type))
                    .divide(BigDecimal.valueOf(previous.size()), 0, RoundingMode.HALF_UP).intValue();
            shares.add(new MealShare(type, pct[i], null, kcalPerDay));
        }
        return shares;
    }

    /** A logged day that is over: at least one meal and before today. */
    static boolean isFullDay(AnalyticsUserDailyNutritionEntity row, LocalDate today) {
        return ProgressGoalService.isLogged(row, today) && row.getActivityDate().isBefore(today);
    }

    /** The week that holds today: its full logged days, their averages and what today holds so far. */
    private static CurrentWeek currentWeek(ProgressWindow window, LocalDate today,
                                           List<AnalyticsUserDailyNutritionEntity> rows, LocalDate thisMonday,
                                           List<AnalyticsUserDailyNutritionEntity> fullDays, boolean included) {
        Integer todayKcal = null;
        BigDecimal todayProtein = null;
        if (window.contains(today)) {
            AnalyticsUserDailyNutritionEntity todayRow = rows.stream()
                    .filter(row -> row.getActivityDate().equals(today) && ProgressGoalService.isLogged(row, today))
                    .findFirst().orElse(null);
            todayKcal = todayRow == null ? 0 : Objects.requireNonNullElse(todayRow.getTotalCaloriesConsumed(), 0);
            todayProtein = todayRow == null ? BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP)
                    : Objects.requireNonNullElse(todayRow.getTotalProteinGrams(), BigDecimal.ZERO)
                    .setScale(1, RoundingMode.HALF_UP);
        }
        return new CurrentWeek(thisMonday, included, fullDays.size(), averageKcal(fullDays),
                averageGrams(fullDays, AnalyticsUserDailyNutritionEntity::getTotalProteinGrams), todayKcal, todayProtein);
    }

    /** The calorie goal's target, else the newest calorie target a counted day carries. */
    private static Integer calorieTarget(AnalyticsUserGoalProgressEntity goal,
                                         List<AnalyticsUserDailyNutritionEntity> counted) {
        if (goal != null && goal.getTargetValue() != null && goal.getTargetValue().signum() > 0) {
            return goal.getTargetValue().setScale(0, RoundingMode.HALF_UP).intValue();
        }
        Integer latest = null;
        for (AnalyticsUserDailyNutritionEntity row : counted) {
            if (row.getCalorieTarget() != null && row.getCalorieTarget() > 0) {
                latest = row.getCalorieTarget();
            }
        }
        return latest;
    }

    private Map<String, AnalyticsUserGoalProgressEntity> activeGoals(UUID userId) {
        Map<String, AnalyticsUserGoalProgressEntity> byType = new HashMap<>();
        goalProgressRepository.findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(userId,
                        GoalExpiryPolicy.ACTIVE_STATUS)
                .forEach(goal -> byType.merge(goal.getGoalType(), goal, (kept, other) ->
                        Comparator.comparing(AnalyticsUserGoalProgressEntity::getLastEvaluatedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder())).compare(other, kept) > 0 ? other : kept));
        return byType;
    }

    private static BigDecimal goalTarget(AnalyticsUserGoalProgressEntity goal) {
        return goal == null ? null : goal.getTargetValue();
    }

    /** The protein goal's target, else the newest target a logged day carries. */
    private static BigDecimal target(AnalyticsUserGoalProgressEntity goal,
                                     List<AnalyticsUserDailyNutritionEntity> logged) {
        if (goal != null && goal.getTargetValue() != null) {
            return goal.getTargetValue();
        }
        BigDecimal latest = null;
        for (AnalyticsUserDailyNutritionEntity row : logged) {
            if (row.getProteinTargetGrams() != null) {
                latest = row.getProteinTargetGrams();
            }
        }
        return latest;
    }

    static Integer averageKcal(List<AnalyticsUserDailyNutritionEntity> logged) {
        if (logged.isEmpty()) {
            return null;
        }
        long sum = logged.stream().mapToLong(row -> Objects.requireNonNullElse(row.getTotalCaloriesConsumed(), 0)).sum();
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(logged.size()), 0, RoundingMode.HALF_UP).intValue();
    }

    static BigDecimal averageGrams(List<AnalyticsUserDailyNutritionEntity> logged,
                                   Function<AnalyticsUserDailyNutritionEntity, BigDecimal> grams) {
        if (logged.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (AnalyticsUserDailyNutritionEntity row : logged) {
            sum = sum.add(Objects.requireNonNullElse(grams.apply(row), BigDecimal.ZERO));
        }
        return sum.divide(BigDecimal.valueOf(logged.size()), 1, RoundingMode.HALF_UP);
    }

    /** Weekly averages over the logged days of each Monday week, only the days inside the period. */
    static Macro macro(ProgressWindow window, List<AnalyticsUserDailyNutritionEntity> logged,
                       Function<AnalyticsUserDailyNutritionEntity, BigDecimal> grams, BigDecimal target) {
        TreeMap<LocalDate, List<AnalyticsUserDailyNutritionEntity>> byWeek = new TreeMap<>();
        logged.stream().filter(row -> window.contains(row.getActivityDate()))
                .forEach(row -> byWeek.computeIfAbsent(ProgressWindow.weekStart(row.getActivityDate()),
                        key -> new ArrayList<>()).add(row));
        List<WeekValue> series = new ArrayList<>();
        byWeek.forEach((monday, days) -> series.add(new WeekValue(monday, averageGrams(days, grams))));
        BigDecimal first = series.isEmpty() ? null : series.get(0).value();
        BigDecimal last = series.isEmpty() ? null : series.get(series.size() - 1).value();
        return new Macro(first, last, target, series);
    }

    /** UP or DOWN from 8% off the first week, otherwise STEADY. */
    static String trend(BigDecimal first, BigDecimal last) {
        int drift = ProgressHighlightRules.drift(first, last);
        return drift > 0 ? UP : drift < 0 ? DOWN : STEADY;
    }

    private static void addLine(List<SummaryLine> lines, String name, Macro macro) {
        if (macro.firstWeek() == null) {
            return;
        }
        lines.add(new SummaryLine(name + " " + ProgressHighlightRules.plain(macro.firstWeek(), 0) + " → "
                + ProgressHighlightRules.plain(macro.thisWeek(), 0) + " g a day", trend(macro.firstWeek(), macro.thisWeek())));
    }

    /** Calories by food group over the logged days, drinks left out, with shares that add up to 100.0. */
    static List<PlateGroup> plate(List<AnalyticsUserDailyNutritionEntity> logged) {
        Map<String, Long> kcal = new HashMap<>();
        for (AnalyticsUserDailyNutritionEntity row : logged) {
            parseGroups(row.getFoodGroupCalories()).forEach((group, value) -> {
                if (!DRINKS.equals(group) && value > 0) {
                    kcal.merge(group, (long) value, Long::sum);
                }
            });
        }
        List<String> groups = new ArrayList<>(kcal.keySet());
        groups.sort(Comparator.comparingInt((String group) -> {
            int index = PLATE_ORDER.indexOf(group);
            return index < 0 ? PLATE_ORDER.size() : index;
        }).thenComparing(Comparator.naturalOrder()));
        List<Long> values = groups.stream().map(kcal::get).toList();
        int[] tenths = largestRemainder(values, 1000);
        List<PlateGroup> plate = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            plate.add(new PlateGroup(groups.get(i), Math.toIntExact(values.get(i)),
                    BigDecimal.valueOf(tenths[i]).movePointLeft(1).setScale(1, RoundingMode.UNNECESSARY)));
        }
        return plate;
    }

    /** The three groups whose share moved most, largest first; empty without a previous plate. */
    static List<PlateChange> plateChanges(List<PlateGroup> now, List<PlateGroup> before) {
        if (now.isEmpty() || before.isEmpty()) {
            return List.of();
        }
        Set<String> groups = new HashSet<>();
        now.forEach(group -> groups.add(group.group()));
        before.forEach(group -> groups.add(group.group()));
        List<PlateChange> changes = new ArrayList<>();
        for (String group : groups) {
            changes.add(new PlateChange(group, share(now, group).subtract(share(before, group))));
        }
        changes.sort(Comparator.comparing((PlateChange change) -> change.deltaPoints().abs()).reversed()
                .thenComparing(PlateChange::group));
        return List.copyOf(changes.subList(0, Math.min(PLATE_CHANGES, changes.size())));
    }

    static BigDecimal share(List<PlateGroup> plate, String group) {
        return plate.stream().filter(item -> item.group().equals(group)).map(PlateGroup::share).findFirst()
                .orElse(BigDecimal.ZERO.setScale(1, RoundingMode.UNNECESSARY));
    }

    /**
     * Each meal type's share of the logged days' calories, in whole percent adding up to 100, with the change in
     * points from the period before. Breakfast, lunch, dinner and snack are always listed; other only with meals.
     */
    static List<MealShare> mealShares(List<AnalyticsMealContributionEntity> meals,
                                      List<AnalyticsUserDailyNutritionEntity> current,
                                      List<AnalyticsUserDailyNutritionEntity> previous) {
        Set<LocalDate> currentDays = new HashSet<>();
        current.forEach(row -> currentDays.add(row.getActivityDate()));
        Set<LocalDate> previousDays = new HashSet<>();
        previous.forEach(row -> previousDays.add(row.getActivityDate()));
        Map<String, Long> now = new LinkedHashMap<>();
        Map<String, Long> before = new LinkedHashMap<>();
        MEAL_ORDER.forEach(type -> {
            now.put(type, 0L);
            before.put(type, 0L);
        });
        for (AnalyticsMealContributionEntity meal : meals) {
            String type = mealType(AnalyticsEnergyService.mealGroup(meal.getMealType()));
            long kcal = Objects.requireNonNullElse(meal.getTotalCalories(), 0);
            if (currentDays.contains(meal.getActivityDate())) {
                now.merge(type, kcal, Long::sum);
            } else if (previousDays.contains(meal.getActivityDate())) {
                before.merge(type, kcal, Long::sum);
            }
        }
        long nowTotal = now.values().stream().mapToLong(Long::longValue).sum();
        if (nowTotal <= 0) {
            return List.of();
        }
        long beforeTotal = before.values().stream().mapToLong(Long::longValue).sum();
        int[] nowPct = largestRemainder(List.copyOf(now.values()), 100);
        int[] beforePct = beforeTotal <= 0 ? null : largestRemainder(List.copyOf(before.values()), 100);
        List<MealShare> shares = new ArrayList<>();
        for (int i = 0; i < MEAL_ORDER.size(); i++) {
            String type = MEAL_ORDER.get(i);
            boolean other = "OTHER".equals(type);
            if (other && now.get(type) == 0 && before.get(type) == 0) {
                continue;
            }
            int kcalPerDay = BigDecimal.valueOf(now.get(type))
                    .divide(BigDecimal.valueOf(current.size()), 0, RoundingMode.HALF_UP).intValue();
            shares.add(new MealShare(type, nowPct[i], beforePct == null ? null : nowPct[i] - beforePct[i], kcalPerDay));
        }
        return shares;
    }

    /** The energy view's meal group as the upper-case type the page lists. */
    private static String mealType(String group) {
        return switch (group) {
            case "breakfast" -> "BREAKFAST";
            case "lunch" -> "LUNCH";
            case "dinner" -> "DINNER";
            case "snack" -> "SNACK";
            default -> "OTHER";
        };
    }

    private static ProgressHighlight largestMeal(List<MealShare> shares, Integer eaten) {
        if (shares.isEmpty() || eaten == null) {
            return null;
        }
        MealShare largest = shares.stream().max(Comparator.comparingInt(MealShare::sharePct)).orElseThrow();
        List<ProgressHighlight.Point> points = shares.stream()
                .map(share -> new ProgressHighlight.Point(share.mealType(), (double) share.sharePct())).toList();
        return ProgressHighlightRules.largestMeal(largest.mealType(), largest.sharePct(), largest.kcalPerDay(),
                eaten, points);
    }

    /**
     * Splits {@code scale} units over the values in proportion, rounding down and handing the units left over
     * to the largest remainders, so the parts always add up to {@code scale} (all zero when the total is zero).
     */
    static int[] largestRemainder(List<Long> values, int scale) {
        int[] parts = new int[values.size()];
        long total = values.stream().mapToLong(Long::longValue).sum();
        if (total <= 0) {
            return parts;
        }
        long[] remainders = new long[values.size()];
        int given = 0;
        for (int i = 0; i < values.size(); i++) {
            long scaled = values.get(i) * scale;
            parts[i] = (int) (scaled / total);
            remainders[i] = scaled % total;
            given += parts[i];
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingLong((Integer i) -> remainders[i]).reversed().thenComparingInt(i -> i));
        for (int k = 0; given < scale; k++, given++) {
            parts[order.get(k % order.size())]++;
        }
        return parts;
    }

    /** Reads the internal "CODE=kcal;CODE=kcal" format of the food-group columns; malformed parts are skipped. */
    static Map<String, Integer> parseGroups(String encoded) {
        Map<String, Integer> groups = new HashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return groups;
        }
        for (String part : encoded.split(";")) {
            int separator = part.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            try {
                groups.merge(part.substring(0, separator).trim(), Integer.parseInt(part.substring(separator + 1).trim()),
                        Integer::sum);
            } catch (NumberFormatException ex) {
                // A part that is not a number is ignored rather than failing the whole page.
            }
        }
        return groups;
    }

    private static void addIfFired(List<ProgressHighlight> highlights, ProgressHighlight highlight) {
        if (highlight != null) {
            highlights.add(highlight);
        }
    }
}
