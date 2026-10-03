package com.fitsync.workout.progress;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.dto.progress.SeriesPoint;
import com.fitsync.workout.dto.progress.SummaryLine;
import com.fitsync.workout.dto.progress.Trend;
import com.fitsync.workout.dto.progress.TrainingProgressView.BestSet;
import com.fitsync.workout.dto.progress.TrainingProgressView.Lift;

/**
 * Figures shared by the Training page, the activity page and the weekly card: records of the
 * estimated best single, the main lifts, and the three summary lines of an activity. Pure functions
 * over session snapshots sorted by date and start.
 */
public final class ProgressFacts {

    static final int MAX_LIFTS = 4;
    static final int MIN_LIFT_SESSIONS = 3;
    private static final double LIFT_TREND_PCT = 2.0;
    private static final double COUNT_TREND_RATIO = 0.10;
    private static final double SIZE_TREND_RATIO = 0.05;
    private static final double PACE_TREND_RATIO = 0.02;
    private static final double DAYS_PER_WEEK = 7.0;

    private ProgressFacts() {
    }

    /** A new estimated best single: its value and how much it beat the previous best. */
    public record RecordEvent(LocalDate date, UUID sessionId, UUID exerciseId, String name, BigDecimal value,
                              BigDecimal delta) {
    }

    /** A session and an exercise in it, to mark the points that are records. */
    public record SessionExercise(UUID sessionId, UUID exerciseId) {
    }

    /** The three lines and the line of context of one activity. */
    public record ActivitySummary(List<SummaryLine> lines, String context) {

        public ActivitySummary {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }

        @Override
        public List<SummaryLine> lines() {
            return List.copyOf(lines);
        }
    }

    /**
     * Walks the sessions from {@code fromDate} on and returns every new estimated best single. The best
     * before that date seeds each exercise; an exercise seen for the first time sets its best without
     * being a record. Values compare at one decimal, as they are shown.
     */
    public static List<RecordEvent> records(List<SessionSnap> sessions, Map<UUID, BigDecimal> bestBefore,
                                            LocalDate fromDate) {
        Map<UUID, BigDecimal> best = new HashMap<>();
        bestBefore.forEach((id, value) -> best.put(id, StrengthMath.round1(value)));
        List<RecordEvent> events = new ArrayList<>();
        for (SessionSnap session : sessions) {
            if (session.date().isBefore(fromDate)) {
                continue;
            }
            for (ExerciseSnap exercise : session.exercises()) {
                BigDecimal value = StrengthMath.round1(exercise.e1rm());
                if (value == null) {
                    continue;
                }
                BigDecimal prior = best.get(exercise.exerciseId());
                if (prior != null && value.compareTo(prior) > 0) {
                    events.add(new RecordEvent(session.date(), session.sessionId(), exercise.exerciseId(),
                            exercise.name(), value, value.subtract(prior)));
                }
                if (prior == null || value.compareTo(prior) > 0) {
                    best.put(exercise.exerciseId(), value);
                }
            }
        }
        return events;
    }

    public static Set<SessionExercise> recordKeys(List<RecordEvent> events) {
        Set<SessionExercise> keys = new HashSet<>();
        for (RecordEvent event : events) {
            keys.add(new SessionExercise(event.sessionId(), event.exerciseId()));
        }
        return keys;
    }

    /**
     * The four most-trained barbell or dumbbell exercises of the period with at least three sessions,
     * by sessions, then sets, then name; each with its estimated best single per session.
     */
    public static List<Lift> lifts(List<SessionSnap> periodSessions, Set<SessionExercise> recordKeys,
                                   BigDecimal bodyWeightKg) {
        Map<UUID, LiftAccumulator> byExercise = new LinkedHashMap<>();
        for (SessionSnap session : periodSessions) {
            for (ExerciseSnap exercise : session.exercises()) {
                if (exercise.equipment() != ExerciseEquipment.BARBELL
                        && exercise.equipment() != ExerciseEquipment.DUMBBELL) {
                    continue;
                }
                SetSnap best = exercise.bestSet();
                if (best == null) {
                    continue;
                }
                LiftAccumulator acc = byExercise.computeIfAbsent(exercise.exerciseId(), id -> new LiftAccumulator());
                acc.name = exercise.name();
                acc.sessions.add(session.sessionId());
                acc.sets += exercise.sets().size();
                boolean record = recordKeys.contains(new SessionExercise(session.sessionId(), exercise.exerciseId()));
                acc.points.add(new SeriesPoint(session.date(), StrengthMath.round1(best.e1rm()), record));
                if (acc.bestValue == null || best.e1rm().compareTo(acc.bestValue) > 0) {
                    acc.bestValue = best.e1rm();
                    acc.bestSet = new BestSet(best.weightKg(), best.reps(), session.date());
                }
            }
        }
        List<Map.Entry<UUID, LiftAccumulator>> ranked = new ArrayList<>(byExercise.entrySet());
        ranked.removeIf(entry -> entry.getValue().sessions.size() < MIN_LIFT_SESSIONS);
        ranked.sort(Comparator.<Map.Entry<UUID, LiftAccumulator>>comparingInt(e -> -e.getValue().sessions.size())
                .thenComparingInt(e -> -e.getValue().sets)
                .thenComparing(e -> e.getValue().name));
        List<Lift> lifts = new ArrayList<>();
        for (Map.Entry<UUID, LiftAccumulator> entry : ranked.subList(0, Math.min(MAX_LIFTS, ranked.size()))) {
            LiftAccumulator acc = entry.getValue();
            BigDecimal first = acc.points.get(0).value();
            BigDecimal last = acc.points.get(acc.points.size() - 1).value();
            lifts.add(new Lift(entry.getKey(), acc.name, acc.points, acc.bestSet,
                    StrengthMath.pctChange(first, last),
                    StrengthMath.relative(acc.name, last, bodyWeightKg), bodyWeightKg));
        }
        return lifts;
    }

    private static final class LiftAccumulator {
        private String name;
        private final Set<UUID> sessions = new HashSet<>();
        private int sets;
        private final List<SeriesPoint> points = new ArrayList<>();
        private BigDecimal bestValue;
        private BestSet bestSet;
    }

    /** The display name of an activity; cardio takes its most logged exercise, as "Treadmill" on the boards. */
    public static String activityName(ActivityKey key, List<SessionSnap> sessions) {
        return switch (key.kind()) {
            case STRENGTH -> "Strength";
            case MOBILITY -> "Mobility";
            case SPORT -> key.sport().label();
            case CARDIO -> mostLoggedName(sessions, "Cardio");
        };
    }

    static String mostLoggedName(List<SessionSnap> sessions, String fallback) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (SessionSnap session : sessions) {
            String name = firstExerciseName(session);
            if (name != null) {
                counts.merge(name, 1, Integer::sum);
            }
        }
        String best = fallback;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }

    static String firstExerciseName(SessionSnap session) {
        return session.exercises().isEmpty() ? null : session.exercises().get(0).name();
    }

    static boolean isRunning(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("run") || lower.contains("treadmill") || lower.contains("jog");
    }

    /**
     * Weeks in a row with at least one session, counted back from the week that holds {@code endDate}.
     * The current week only counts once it has a session, since it is not over yet.
     */
    public static int weeksInARow(List<SessionSnap> sessions, LocalDate endDate) {
        Set<LocalDate> weeks = new HashSet<>();
        for (SessionSnap session : sessions) {
            if (!session.date().isAfter(endDate)) {
                weeks.add(ProgressPeriod.mondayOf(session.date()));
            }
        }
        LocalDate week = ProgressPeriod.mondayOf(endDate);
        if (!weeks.contains(week)) {
            week = week.minusWeeks(1);
        }
        int streak = 0;
        while (weeks.contains(week)) {
            streak++;
            week = week.minusWeeks(1);
        }
        return streak;
    }

    /**
     * The three summary lines and the context of one activity, built from numbers by rules.
     *
     * @param allSessions every loaded session of this activity, for the run of weeks
     * @param lifts       the period's main lifts, used by the strength line on the estimated best single
     */
    public static ActivitySummary summarise(ActivityKey key, String name, List<SessionSnap> period,
                                            List<SessionSnap> previous, List<SessionSnap> allSessions,
                                            ProgressPeriod progressPeriod, LocalDate today, List<Lift> lifts) {
        LocalDate endDate = progressPeriod.to().isAfter(today) ? today : progressPeriod.to();
        double weeks = Math.max(1, elapsedDays(progressPeriod, today)) / DAYS_PER_WEEK;
        double previousWeeks = progressPeriod.days() / DAYS_PER_WEEK;
        List<SummaryLine> lines;
        if (key.kind() == SessionKind.STRENGTH) {
            lines = strengthLines(period, previous, progressPeriod, endDate, weeks, previousWeeks, lifts);
        } else if (key.kind() == SessionKind.CARDIO && period.stream().anyMatch(s -> s.paceSecPerKm() != null)) {
            lines = distanceLines(name, period, previous);
        } else {
            lines = timeLines(period, previous, allSessions, endDate, weeks, previousWeeks);
        }
        long delta = minutes(period) - minutes(previous);
        String label = ProgressText.previousLabel(progressPeriod.days());
        String context = delta == 0 ? "Same time as " + label : ProgressText.signedHm(delta) + " on " + label;
        return new ActivitySummary(lines, context);
    }

    /** Days of the period that have already happened, counting today. */
    static long elapsedDays(ProgressPeriod period, LocalDate today) {
        LocalDate end = period.to().isAfter(today) ? today : period.to();
        if (end.isBefore(period.from())) {
            return 0;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(period.from(), end) + 1;
    }

    private static List<SummaryLine> strengthLines(List<SessionSnap> period, List<SessionSnap> previous,
                                                   ProgressPeriod progressPeriod, LocalDate endDate, double weeks,
                                                   double previousWeeks, List<Lift> lifts) {
        List<SummaryLine> lines = new ArrayList<>();
        if (lifts.isEmpty()) {
            lines.add(line(ProgressText.sessions(period.size()), "in all",
                    trend(period.size(), previous.size(), COUNT_TREND_RATIO)));
        } else {
            double sum = 0;
            for (Lift lift : lifts) {
                List<SeriesPoint> points = lift.e1rm();
                sum += StrengthMath.rawPct(points.get(0).value(), points.get(points.size() - 1).value());
            }
            double average = sum / lifts.size();
            int shown = (int) Math.ceil(average - 0.5);
            Trend trend = average > LIFT_TREND_PCT ? Trend.UP : (average < -LIFT_TREND_PCT ? Trend.DOWN : Trend.STEADY);
            lines.add(line("e1RM " + ProgressText.signedPct(shown),
                    "across " + ProgressText.word(lifts.size()) + (lifts.size() == 1 ? " lift" : " lifts"), trend));
        }
        double setsPerWeek = (double) hardSets(period) / weeks;
        double previousSetsPerWeek = (double) hardSets(previous) / previousWeeks;
        long shownSets = Math.round(setsPerWeek);
        lines.add(line(shownSets + (shownSets == 1 ? " hard set" : " hard sets"), "a week",
                trend(setsPerWeek, previousSetsPerWeek, COUNT_TREND_RATIO)));
        int[] range = weeklyRange(period, progressPeriod.from(), endDate);
        String figure;
        if (range[0] == range[1]) {
            figure = ProgressText.sessions(range[0]);
        } else if (range[1] - range[0] == 1) {
            figure = range[0] + " or " + range[1] + " sessions";
        } else {
            figure = range[0] + " to " + range[1] + " sessions";
        }
        lines.add(line(figure, "a week", trend(period.size() / weeks, previous.size() / previousWeeks,
                COUNT_TREND_RATIO)));
        return lines;
    }

    /** The fewest and most sessions in a Monday week, over the whole weeks inside the period when there are any. */
    static int[] weeklyRange(List<SessionSnap> period, LocalDate from, LocalDate endDate) {
        Map<LocalDate, Integer> counts = new HashMap<>();
        for (SessionSnap session : period) {
            counts.merge(ProgressPeriod.mondayOf(session.date()), 1, Integer::sum);
        }
        List<LocalDate> whole = new ArrayList<>();
        List<LocalDate> any = new ArrayList<>();
        for (LocalDate week = ProgressPeriod.mondayOf(from); !week.isAfter(endDate); week = week.plusWeeks(1)) {
            any.add(week);
            if (!week.isBefore(from) && !week.plusDays(6).isAfter(endDate)) {
                whole.add(week);
            }
        }
        List<LocalDate> weeks = whole.isEmpty() ? any : whole;
        int min = Integer.MAX_VALUE;
        int max = 0;
        for (LocalDate week : weeks) {
            int count = counts.getOrDefault(week, 0);
            min = Math.min(min, count);
            max = Math.max(max, count);
        }
        return new int[] {min == Integer.MAX_VALUE ? 0 : min, max};
    }

    private static List<SummaryLine> distanceLines(String name, List<SessionSnap> period, List<SessionSnap> previous) {
        List<SummaryLine> lines = new ArrayList<>();
        List<SessionSnap> paced = period.stream().filter(s -> s.paceSecPerKm() != null).toList();
        int firstPace = paced.get(0).paceSecPerKm();
        int lastPace = paced.get(paced.size() - 1).paceSecPerKm();
        if (paced.size() >= 2) {
            Trend trend = lastPace < firstPace * (1 - PACE_TREND_RATIO) ? Trend.UP
                    : (lastPace > firstPace * (1 + PACE_TREND_RATIO) ? Trend.DOWN : Trend.STEADY);
            lines.add(line("Pace " + ProgressText.pace(firstPace) + " → " + ProgressText.pace(lastPace),
                    "a km", trend));
        } else {
            lines.add(line("Pace " + ProgressText.pace(firstPace), "a km", Trend.STEADY));
        }
        String label = isRunning(name) ? "Longest run" : "Longest";
        BigDecimal longest = maxDistance(period);
        BigDecimal longestBefore = maxDistance(previous);
        if (longestBefore != null) {
            lines.add(line(label + " " + ProgressText.kg(longestBefore) + " → " + ProgressText.kg(longest) + " km",
                    "", trend(longest.doubleValue(), longestBefore.doubleValue(), SIZE_TREND_RATIO)));
        } else {
            lines.add(line(label + " " + ProgressText.kg(longest) + " km", "", Trend.STEADY));
        }
        BigDecimal total = totalDistance(period);
        lines.add(line(ProgressText.kg(total) + " km", "in all",
                trend(total.doubleValue(), totalDistance(previous).doubleValue(), COUNT_TREND_RATIO)));
        return lines;
    }

    private static List<SummaryLine> timeLines(List<SessionSnap> period, List<SessionSnap> previous,
                                               List<SessionSnap> allSessions, LocalDate endDate, double weeks,
                                               double previousWeeks) {
        List<SummaryLine> lines = new ArrayList<>();
        double perWeek = period.size() / weeks;
        Trend frequencyTrend = trend(perWeek, previous.size() / previousWeeks, COUNT_TREND_RATIO);
        int streak = weeksInARow(allSessions, endDate);
        String frequency = frequency(perWeek);
        if (streak >= 2) {
            lines.add(line(frequency + ",", streak + " weeks in a row", frequencyTrend));
        } else {
            lines.add(line(frequency, "", frequencyTrend));
        }
        if (period.isEmpty()) {
            lines.add(line("No sessions", "in this period", Trend.STEADY));
        } else if (period.size() == 1) {
            lines.add(line(period.get(0).minutes() + " min", "a session", Trend.STEADY));
        } else {
            int first = period.get(0).minutes();
            int last = period.get(period.size() - 1).minutes();
            lines.add(line(first + " → " + last + " min", "a session", trend(last, first, SIZE_TREND_RATIO)));
        }
        List<Integer> efforts = period.stream().map(SessionSnap::effort).filter(e -> e != null).toList();
        if (efforts.size() >= 2) {
            int first = efforts.get(0);
            int last = efforts.get(efforts.size() - 1);
            Trend trend = last > first ? Trend.UP : (last < first ? Trend.DOWN : Trend.STEADY);
            lines.add(line("Effort " + first + " → " + last, "of 10", trend));
        } else if (efforts.size() == 1) {
            lines.add(line("Effort " + efforts.get(0), "of 10", Trend.STEADY));
        } else {
            lines.add(line(ProgressText.hm(minutes(period)), "in all",
                    trend(minutes(period), minutes(previous), COUNT_TREND_RATIO)));
        }
        return lines;
    }

    /** "Once a week", "Twice a week", "3 times a week", or "Now and then" under one every two weeks. */
    static String frequency(double perWeek) {
        if (perWeek < 0.5) {
            return "Now and then";
        }
        long rounded = Math.round(perWeek);
        if (rounded == 1) {
            return "Once a week";
        }
        if (rounded == 2) {
            return "Twice a week";
        }
        return rounded + " times a week";
    }

    /** UP or DOWN when now moved more than the ratio from before; UP when something started from nothing. */
    static Trend trend(double now, double before, double ratio) {
        if (before <= 0) {
            return now > 0 ? Trend.UP : Trend.STEADY;
        }
        double change = (now - before) / before;
        if (change > ratio) {
            return Trend.UP;
        }
        if (change < -ratio) {
            return Trend.DOWN;
        }
        return Trend.STEADY;
    }

    static SummaryLine line(String figure, String rest, Trend trend) {
        return new SummaryLine(figure, rest.isEmpty() ? figure : figure + " " + rest, trend);
    }

    public static long minutes(List<SessionSnap> sessions) {
        long total = 0;
        for (SessionSnap session : sessions) {
            total += session.minutes();
        }
        return total;
    }

    /** Sets with work in them across these sessions, the count the muscle figure also builds on. */
    static long hardSets(List<SessionSnap> sessions) {
        long total = 0;
        for (SessionSnap session : sessions) {
            for (ExerciseSnap exercise : session.exercises()) {
                total += exercise.sets().stream().filter(SetSnap::hasWork).count();
            }
        }
        return total;
    }

    public static BigDecimal volume(List<SessionSnap> sessions) {
        BigDecimal total = BigDecimal.ZERO;
        for (SessionSnap session : sessions) {
            for (ExerciseSnap exercise : session.exercises()) {
                total = total.add(exercise.volume());
            }
        }
        return total;
    }

    private static BigDecimal maxDistance(List<SessionSnap> sessions) {
        BigDecimal max = null;
        for (SessionSnap session : sessions) {
            BigDecimal distance = session.distanceKm();
            if (distance != null && distance.signum() > 0 && (max == null || distance.compareTo(max) > 0)) {
                max = distance;
            }
        }
        return max;
    }

    private static BigDecimal totalDistance(List<SessionSnap> sessions) {
        BigDecimal total = BigDecimal.ZERO;
        for (SessionSnap session : sessions) {
            if (session.distanceKm() != null && session.distanceKm().signum() > 0) {
                total = total.add(session.distanceKm());
            }
        }
        return total;
    }

    /** Sessions of one activity, in the order given. */
    public static List<SessionSnap> ofActivity(List<SessionSnap> sessions, String key) {
        return sessions.stream().filter(s -> s.activityKey().equals(key)).toList();
    }

    /** The order activities are listed in: strength, cardio, each sport by minutes, mobility. */
    static int kindRank(SessionKind kind) {
        return switch (kind) {
            case STRENGTH -> 0;
            case CARDIO -> 1;
            case SPORT -> 2;
            case MOBILITY -> 3;
        };
    }

    static ActivityKey keyOf(SessionSnap session) {
        if (session.kind() == SessionKind.SPORT) {
            return new ActivityKey(SessionKind.SPORT, session.sport() == null ? SportActivity.OTHER : session.sport());
        }
        return new ActivityKey(session.kind(), null);
    }
}
