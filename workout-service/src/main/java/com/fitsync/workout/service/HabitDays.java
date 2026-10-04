package com.fitsync.workout.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;

/**
 * Finds the activities a person does on a fixed weekday: the same activity on the same weekday in
 * consecutive weeks, counted back from this week (or from last week when this week's day has not come
 * yet). An activity is keyed the way Progress keys it: STRENGTH, CARDIO, MOBILITY or SPORT:&lt;activity&gt;.
 * A sport with a run of three weeks or more is a habit day of the default week. A pure function of the
 * person's completed sessions.
 */
public final class HabitDays {

    static final int MIN_WEEKS = 3;
    private static final String SPORT_PREFIX = "SPORT:";

    private HabitDays() {
    }

    /** One completed session as the rule needs it. */
    public record SessionDay(LocalDate date, SessionKind kind, SportActivity sport, int minutes) {

        String activityKey() {
            if (kind == SessionKind.SPORT) {
                return SPORT_PREFIX + (sport == null ? SportActivity.OTHER : sport).name();
            }
            return kind == null ? "" : kind.name();
        }
    }

    /** A sport on a fixed weekday, with its usual minutes and how many weeks in a row it has run. */
    public record Habit(DayOfWeek weekday, SportActivity sport, int minutes, int weeks) {
    }

    /** An activity's longest current run on one weekday: the weekday, the usual minutes, the weeks. */
    public record Run(String key, DayOfWeek weekday, int minutes, int weeks) {
    }

    /** The sport habits of three weeks or more, longest run first, then by weekday. */
    public static List<Habit> find(List<SessionDay> sessions, LocalDate today) {
        List<Habit> habits = new ArrayList<>();
        for (Run run : runs(sessions, today).values()) {
            if (run.weeks() >= MIN_WEEKS && run.key().startsWith(SPORT_PREFIX)) {
                SportActivity sport = SportActivity.valueOf(run.key().substring(SPORT_PREFIX.length()));
                habits.add(new Habit(run.weekday(), sport, run.minutes(), run.weeks()));
            }
        }
        habits.sort(Comparator.comparingInt(Habit::weeks).reversed().thenComparing(Habit::weekday));
        return habits;
    }

    /**
     * Each activity's best current run: the weekday with the most consecutive weeks up to now (the
     * earlier weekday on a tie). Activities without a run of at least one week are left out.
     */
    public static Map<String, Run> runs(List<SessionDay> sessions, LocalDate today) {
        Map<String, Set<LocalDate>> weeksByKey = new HashMap<>();
        Map<String, List<Integer>> minutesByKey = new HashMap<>();
        for (SessionDay session : sessions) {
            if (session.kind() == null || session.date() == null) {
                continue;
            }
            String key = session.activityKey() + "#" + session.date().getDayOfWeek().getValue();
            weeksByKey.computeIfAbsent(key, k -> new HashSet<>()).add(weekStart(session.date()));
            minutesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(session.minutes());
        }
        LocalDate thisWeek = weekStart(today);
        Map<String, Run> best = new HashMap<>();
        for (Map.Entry<String, Set<LocalDate>> entry : weeksByKey.entrySet()) {
            Set<LocalDate> weeks = entry.getValue();
            LocalDate week = weeks.contains(thisWeek) ? thisWeek : thisWeek.minusWeeks(1);
            int length = 0;
            while (weeks.contains(week)) {
                length++;
                week = week.minusWeeks(1);
            }
            if (length == 0) {
                continue;
            }
            int split = entry.getKey().lastIndexOf('#');
            String activity = entry.getKey().substring(0, split);
            DayOfWeek weekday = DayOfWeek.of(Integer.parseInt(entry.getKey().substring(split + 1)));
            Run run = new Run(activity, weekday, median(minutesByKey.get(entry.getKey())), length);
            best.merge(activity, run, (current, candidate) ->
                    candidate.weeks() > current.weeks()
                            || (candidate.weeks() == current.weeks() && candidate.weekday().compareTo(current.weekday()) < 0)
                            ? candidate : current);
        }
        return best;
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static int median(List<Integer> values) {
        List<Integer> sorted = values.stream().filter(value -> value > 0).sorted().toList();
        return sorted.isEmpty() ? 0 : sorted.get((sorted.size() - 1) / 2);
    }
}
