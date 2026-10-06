package com.fitsync.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.fitsync.analytics.dto.AnalyticsAchievementsResponse.Evidence;
import com.fitsync.analytics.dto.AnalyticsAchievementsResponse.Progress;
import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;
import com.fitsync.analytics.entity.AnalyticsUserDailyWorkoutEntity;

/**
 * Works out every badge from the rollups, without any storage: whether it is earned by the data as it
 * stands, on which day it was first earned, its progress and its evidence. The caller decides what is
 * stored; a badge that is stored stays earned whatever this says later.
 *
 * <p>Days are the user's own days, as the rollups key them. Today is still going, so it never spoils a
 * protein week or month, and a week or month only earns a protein badge once it is over.
 */
final class BadgeCalculator {

    static final int PROTEIN_WEEK_DAYS = 5;
    static final int PROTEIN_MONTH_DAYS = 20;
    static final int PACE_WEEKS = 4;
    private static final String SESSIONS = "sessions";
    private static final String DAYS = "days";

    /**
     * Everything the badges read.
     *
     * @param sessionsTarget    the active sessions a week target, null when none is set
     * @param proteinGoalGrams  the active protein target, used on days whose row carries none
     */
    record Inputs(LocalDate today,
                  ZoneId zone,
                  List<AnalyticsUserDailyWorkoutEntity> workoutDays,
                  List<AnalyticsUserDailyNutritionEntity> nutritionDays,
                  Integer sessionsTarget,
                  BigDecimal proteinGoalGrams,
                  int personalRecords,
                  Instant firstRecordAt,
                  Instant lastRecordAt,
                  int cooks,
                  Instant firstCookAt,
                  Instant lastCookAt,
                  LocalDate firstWeightDate) {

        Inputs {
            workoutDays = List.copyOf(workoutDays);
            nutritionDays = List.copyOf(nutritionDays);
        }

        @Override
        public List<AnalyticsUserDailyWorkoutEntity> workoutDays() {
            return List.copyOf(workoutDays);
        }

        @Override
        public List<AnalyticsUserDailyNutritionEntity> nutritionDays() {
            return List.copyOf(nutritionDays);
        }
    }

    /** The n-th completed session (1-based) of a day, for a session-count badge's evidence. */
    record SessionRef(LocalDate date, int indexInDay) {
    }

    /**
     * One badge as the data stands.
     *
     * @param earnedOn null when the data does not earn it
     * @param session  for a session-count badge, the session that crossed the line
     */
    record Computed(String code, Progress progress, LocalDate earnedOn, Evidence evidence, SessionRef session) {

        boolean earned() {
            return earnedOn != null;
        }
    }

    record Result(Map<String, Computed> badges, BigDecimal sessionsPerWeek) {

        Result {
            badges = Map.copyOf(badges);
        }

        @Override
        public Map<String, Computed> badges() {
            return Map.copyOf(badges);
        }
    }

    private BadgeCalculator() {
    }

    static Result compute(Inputs in) {
        Map<String, Computed> out = new HashMap<>();
        TreeMap<LocalDate, Integer> sessions = sessionsByDay(in.workoutDays());
        sessionCount(out, sessions, BadgeCatalogue.FIRST_WORKOUT, 1);
        sessionCount(out, sessions, BadgeCatalogue.WORKOUTS_10, 10);
        sessionCount(out, sessions, BadgeCatalogue.WORKOUTS_50, 50);
        sessionCount(out, sessions, BadgeCatalogue.WORKOUTS_100, 100);
        fullWeeks(out, sessions, in.sessionsTarget(), in.today());

        recordCount(out, BadgeCatalogue.FIRST_RECORD, 1, in.personalRecords(), in.firstRecordAt(), in);
        recordCount(out, BadgeCatalogue.FIVE_RECORDS, 5, in.personalRecords(), in.lastRecordAt(), in);
        // No bench figure reaches analytics: the personal-record projection keeps a count only, so this
        // badge has no progress and cannot be earned until such a figure is projected.
        out.put(BadgeCatalogue.BODYWEIGHT_BENCH,
                new Computed(BadgeCatalogue.BODYWEIGHT_BENCH, null, null, null, null));

        List<LocalDate> logged = loggedDays(in.nutritionDays(), in.today());
        firstMeal(out, logged);
        loggedRun(out, logged, BadgeCatalogue.SEVEN_DAYS_LOGGED, 7, in.today());
        loggedRun(out, logged, BadgeCatalogue.THIRTY_DAYS_LOGGED, 30, in.today());
        protein(out, in);

        cookCount(out, BadgeCatalogue.FIRST_COOK, 1, in.cooks(), in.firstCookAt(), in);
        cookCount(out, BadgeCatalogue.TEN_COOKS, 10, in.cooks(), in.lastCookAt(), in);
        cookCount(out, BadgeCatalogue.FIFTY_COOKS, 50, in.cooks(), in.lastCookAt(), in);

        trainingRun(out, sessions, BadgeCatalogue.STREAK_7, 7);
        trainingRun(out, sessions, BadgeCatalogue.STREAK_30, 30);
        out.put(BadgeCatalogue.FIRST_WEIGHT_LOGGED, new Computed(BadgeCatalogue.FIRST_WEIGHT_LOGGED, null,
                in.firstWeightDate(), null, null));

        return new Result(out, sessionsPerWeek(sessions, in.today()));
    }

    // ---- Showing up ----------------------------------------------------------------------------------

    private static TreeMap<LocalDate, Integer> sessionsByDay(List<AnalyticsUserDailyWorkoutEntity> rows) {
        TreeMap<LocalDate, Integer> sessions = new TreeMap<>();
        for (AnalyticsUserDailyWorkoutEntity row : rows) {
            int count = Objects.requireNonNullElse(row.getCompletedWorkoutCount(), 0);
            if (count > 0 && row.getActivityDate() != null) {
                sessions.merge(row.getActivityDate(), count, Integer::sum);
            }
        }
        return sessions;
    }

    private static void sessionCount(Map<String, Computed> out, TreeMap<LocalDate, Integer> sessions, String code,
                                     int target) {
        int total = 0;
        LocalDate earnedOn = null;
        SessionRef ref = null;
        for (Map.Entry<LocalDate, Integer> day : sessions.entrySet()) {
            int before = total;
            total += day.getValue();
            if (earnedOn == null && total >= target) {
                earnedOn = day.getKey();
                ref = new SessionRef(day.getKey(), target - before);
            }
        }
        Evidence evidence = earnedOn == null ? null : new Evidence(null, null, null, null, null);
        out.put(code, new Computed(code, progress(total, target, SESSIONS), earnedOn, evidence, ref));
    }

    /** FULL_WEEK and FOUR_FULL_WEEKS: Monday-to-Sunday weeks whose sessions reach the target. */
    private static void fullWeeks(Map<String, Computed> out, TreeMap<LocalDate, Integer> sessions, Integer target,
                                  LocalDate today) {
        if (target == null || target <= 0) {
            out.put(BadgeCatalogue.FULL_WEEK, new Computed(BadgeCatalogue.FULL_WEEK, null, null, null, null));
            out.put(BadgeCatalogue.FOUR_FULL_WEEKS,
                    new Computed(BadgeCatalogue.FOUR_FULL_WEEKS, null, null, null, null));
            return;
        }
        LocalDate thisMonday = monday(today);
        LocalDate firstFull = null;
        LocalDate firstFullMonday = null;
        LocalDate fourOn = null;
        LocalDate fourMonday = null;
        int run = 0;
        int runEndingLastWeek = 0;
        int thisWeek = 0;
        boolean thisWeekMet = false;
        LocalDate start = sessions.isEmpty() ? thisMonday : monday(sessions.firstKey());
        for (LocalDate week = start; !week.isAfter(thisMonday); week = week.plusWeeks(1)) {
            LocalDate metOn = metOn(sessions, week, target);
            if (week.equals(thisMonday)) {
                thisWeek = sessionsIn(sessions, week);
                thisWeekMet = metOn != null;
                runEndingLastWeek = run;
            }
            if (metOn == null) {
                run = 0;
                continue;
            }
            run++;
            if (firstFull == null) {
                firstFull = metOn;
                firstFullMonday = week;
            }
            if (fourOn == null && run >= 4) {
                fourOn = metOn;
                fourMonday = week;
            }
        }
        out.put(BadgeCatalogue.FULL_WEEK, new Computed(BadgeCatalogue.FULL_WEEK,
                progress(thisWeek, target, SESSIONS), firstFull,
                firstFull == null ? null : weekEvidence(sessions, firstFullMonday), null));
        int currentRun = thisWeekMet ? runEndingLastWeek + 1 : runEndingLastWeek;
        out.put(BadgeCatalogue.FOUR_FULL_WEEKS, new Computed(BadgeCatalogue.FOUR_FULL_WEEKS,
                progress(currentRun, 4, "weeks"), fourOn,
                fourOn == null ? null : weekEvidence(sessions, fourMonday), null));
    }

    /** The day a week's sessions first reach the target, or null when they never do. */
    private static LocalDate metOn(TreeMap<LocalDate, Integer> sessions, LocalDate monday, int target) {
        int count = 0;
        for (int d = 0; d < 7; d++) {
            LocalDate day = monday.plusDays(d);
            count += sessions.getOrDefault(day, 0);
            if (count >= target) {
                return day;
            }
        }
        return null;
    }

    private static int sessionsIn(TreeMap<LocalDate, Integer> sessions, LocalDate monday) {
        int count = 0;
        for (int d = 0; d < 7; d++) {
            count += sessions.getOrDefault(monday.plusDays(d), 0);
        }
        return count;
    }

    private static Evidence weekEvidence(TreeMap<LocalDate, Integer> sessions, LocalDate monday) {
        List<Boolean> days = new ArrayList<>();
        for (int d = 0; d < 7; d++) {
            days.add(sessions.getOrDefault(monday.plusDays(d), 0) > 0);
        }
        return new Evidence(monday, null, null, null, days);
    }

    /** Retired streak badges: the first day a run of consecutive training days reached the length. */
    private static void trainingRun(Map<String, Computed> out, TreeMap<LocalDate, Integer> sessions, String code,
                                    int length) {
        LocalDate earnedOn = firstRunReaching(new ArrayList<>(sessions.keySet()), length);
        out.put(code, new Computed(code, null, earnedOn, null, null));
    }

    private static BigDecimal sessionsPerWeek(TreeMap<LocalDate, Integer> sessions, LocalDate today) {
        LocalDate to = monday(today).minusDays(1);
        LocalDate from = monday(today).minusWeeks(PACE_WEEKS);
        int count = 0;
        for (Map.Entry<LocalDate, Integer> day : sessions.subMap(from, true, to, true).entrySet()) {
            count += day.getValue();
        }
        return BigDecimal.valueOf(count).divide(BigDecimal.valueOf(PACE_WEEKS), 1, RoundingMode.HALF_UP);
    }

    // ---- Getting stronger and the kitchen -------------------------------------------------------------

    private static void recordCount(Map<String, Computed> out, String code, int target, int records,
                                    Instant crossedAt, Inputs in) {
        out.put(code, counted(code, target, records, crossedAt, in, "records"));
    }

    private static void cookCount(Map<String, Computed> out, String code, int target, int cooks, Instant crossedAt,
                                  Inputs in) {
        out.put(code, counted(code, target, cooks, crossedAt, in, "cooks"));
    }

    /**
     * A counter badge. The counters keep only their first and last instants, so the first badge uses the
     * first instant and the higher ones the last, which is the latest the line can have been crossed.
     */
    private static Computed counted(String code, int target, int count, Instant crossedAt, Inputs in, String unit) {
        LocalDate earnedOn = null;
        if (count >= target) {
            earnedOn = crossedAt == null ? in.today() : LocalDate.ofInstant(crossedAt, in.zone());
        }
        return new Computed(code, progress(count, target, unit), earnedOn,
                earnedOn == null ? null : new Evidence(null, null, null, null, null), null);
    }

    // ---- Eating well ---------------------------------------------------------------------------------

    /** Days with at least one meal; a fast day is a meal of its own, so it counts. */
    private static List<LocalDate> loggedDays(List<AnalyticsUserDailyNutritionEntity> rows, LocalDate today) {
        return rows.stream()
                .filter(row -> row.getActivityDate() != null && !row.getActivityDate().isAfter(today)
                        && Objects.requireNonNullElse(row.getMealCount(), 0) > 0)
                .map(AnalyticsUserDailyNutritionEntity::getActivityDate)
                .sorted()
                .distinct()
                .toList();
    }

    private static void firstMeal(Map<String, Computed> out, List<LocalDate> logged) {
        LocalDate first = logged.isEmpty() ? null : logged.get(0);
        out.put(BadgeCatalogue.FIRST_MEAL, new Computed(BadgeCatalogue.FIRST_MEAL,
                progress(Math.min(logged.size(), 1), 1, DAYS), first,
                first == null ? null : new Evidence(null, null, null, null, null), null));
    }

    private static void loggedRun(Map<String, Computed> out, List<LocalDate> logged, String code, int length,
                                  LocalDate today) {
        LocalDate earnedOn = firstRunReaching(logged, length);
        out.put(code, new Computed(code, progress(currentRun(logged, today), length, DAYS), earnedOn,
                earnedOn == null ? null : new Evidence(null, null, null, null, null), null));
    }

    /** The day a run of consecutive dates first reaches the length, or null. */
    private static LocalDate firstRunReaching(List<LocalDate> dates, int length) {
        int run = 0;
        LocalDate previous = null;
        for (LocalDate date : dates) {
            run = previous != null && previous.plusDays(1).equals(date) ? run + 1 : 1;
            if (run >= length) {
                return date;
            }
            previous = date;
        }
        return null;
    }

    /** The run still going: it ends today, or yesterday while today has nothing logged yet. */
    private static int currentRun(List<LocalDate> dates, LocalDate today) {
        if (dates.isEmpty()) {
            return 0;
        }
        LocalDate last = dates.get(dates.size() - 1);
        if (last.isBefore(today.minusDays(1))) {
            return 0;
        }
        int run = 1;
        for (int i = dates.size() - 1; i > 0; i--) {
            if (!dates.get(i - 1).plusDays(1).equals(dates.get(i))) {
                break;
            }
            run++;
        }
        return run;
    }

    /** A logged day's protein verdict: met, missed, or null when there is no target to judge it by. */
    private static Boolean proteinMet(AnalyticsUserDailyNutritionEntity row, BigDecimal goalGrams) {
        BigDecimal target = row.getProteinTargetGrams() != null && row.getProteinTargetGrams().signum() > 0
                ? row.getProteinTargetGrams()
                : goalGrams != null && goalGrams.signum() > 0 ? goalGrams : null;
        if (target == null) {
            return null;
        }
        BigDecimal eaten = Objects.requireNonNullElse(row.getTotalProteinGrams(), BigDecimal.ZERO);
        return eaten.compareTo(target) >= 0;
    }

    private static void protein(Map<String, Computed> out, Inputs in) {
        LocalDate today = in.today();
        TreeMap<LocalDate, Boolean> met = new TreeMap<>();
        boolean anyTarget = in.proteinGoalGrams() != null && in.proteinGoalGrams().signum() > 0;
        for (AnalyticsUserDailyNutritionEntity row : in.nutritionDays()) {
            if (row.getActivityDate() == null || row.getActivityDate().isAfter(today)
                    || Objects.requireNonNullElse(row.getMealCount(), 0) <= 0) {
                continue;
            }
            Boolean verdict = proteinMet(row, in.proteinGoalGrams());
            anyTarget = anyTarget || verdict != null;
            // A day without any target to judge it by cannot be said to have met one.
            met.put(row.getActivityDate(), Boolean.TRUE.equals(verdict));
        }
        if (!anyTarget) {
            out.put(BadgeCatalogue.PROTEIN_WEEK, new Computed(BadgeCatalogue.PROTEIN_WEEK, null, null, null, null));
            out.put(BadgeCatalogue.PROTEIN_MONTH,
                    new Computed(BadgeCatalogue.PROTEIN_MONTH, null, null, null, null));
            return;
        }
        LocalDate thisMonday = monday(today);
        LocalDate weekEarned = null;
        Evidence weekEvidence = null;
        LocalDate start = met.isEmpty() ? thisMonday : monday(met.firstKey());
        for (LocalDate week = start; week.isBefore(thisMonday) && weekEarned == null; week = week.plusWeeks(1)) {
            if (qualifies(met, week, week.plusDays(6), PROTEIN_WEEK_DAYS)) {
                weekEarned = week.plusDays(6);
                List<Boolean> days = new ArrayList<>();
                for (int d = 0; d < 7; d++) {
                    days.add(Boolean.TRUE.equals(met.get(week.plusDays(d))));
                }
                weekEvidence = new Evidence(week, null, null, null, days);
            }
        }
        out.put(BadgeCatalogue.PROTEIN_WEEK, new Computed(BadgeCatalogue.PROTEIN_WEEK,
                progress(soFar(met, thisMonday, today), PROTEIN_WEEK_DAYS, DAYS), weekEarned, weekEvidence, null));

        YearMonth thisMonth = YearMonth.from(today);
        LocalDate monthEarned = null;
        Evidence monthEvidence = null;
        YearMonth first = met.isEmpty() ? thisMonth : YearMonth.from(met.firstKey());
        for (YearMonth month = first; month.isBefore(thisMonth) && monthEarned == null; month = month.plusMonths(1)) {
            if (qualifies(met, month.atDay(1), month.atEndOfMonth(), PROTEIN_MONTH_DAYS)) {
                monthEarned = month.atEndOfMonth();
                monthEvidence = new Evidence(null, month.atDay(1), null, null, null);
            }
        }
        out.put(BadgeCatalogue.PROTEIN_MONTH, new Computed(BadgeCatalogue.PROTEIN_MONTH,
                progress(soFar(met, thisMonth.atDay(1), today), PROTEIN_MONTH_DAYS, DAYS), monthEarned,
                monthEvidence, null));
    }

    /** A finished period earns a protein badge when it has enough logged days and every one met the target. */
    private static boolean qualifies(TreeMap<LocalDate, Boolean> met, LocalDate from, LocalDate to, int minimumDays) {
        Map<LocalDate, Boolean> period = met.subMap(from, true, to, true);
        return period.size() >= minimumDays && period.values().stream().allMatch(Boolean.TRUE::equals);
    }

    /**
     * The current period's met days, or 0 once a finished day in it missed. Today counts when it is met
     * and is never a miss, since it is still going.
     */
    private static int soFar(TreeMap<LocalDate, Boolean> met, LocalDate from, LocalDate today) {
        int count = 0;
        for (Map.Entry<LocalDate, Boolean> day : met.subMap(from, true, today, true).entrySet()) {
            if (Boolean.TRUE.equals(day.getValue())) {
                count++;
            } else if (day.getKey().isBefore(today)) {
                return 0;
            }
        }
        return count;
    }

    // ---- Shared --------------------------------------------------------------------------------------

    private static LocalDate monday(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static Progress progress(int current, int target, String unit) {
        return new Progress(BigDecimal.valueOf(current), BigDecimal.valueOf(target), unit);
    }
}
