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
 * Finds the sports a person plays on a fixed weekday: the same sport on the same weekday in three or
 * more weeks in a row, counted back from this week (or from last week when this week's day has not come
 * yet). A pure function of the person's completed sessions.
 */
public final class HabitDays {

    static final int MIN_WEEKS = 3;

    private HabitDays() {
    }

    /** One completed session as the rule needs it. */
    public record SessionDay(LocalDate date, SessionKind kind, SportActivity sport, int minutes) {
    }

    /** A sport on a fixed weekday, with its usual minutes and how many weeks in a row it has run. */
    public record Habit(DayOfWeek weekday, SportActivity sport, int minutes, int weeks) {
    }

    /** The habits, longest run first, then by weekday. */
    public static List<Habit> find(List<SessionDay> sessions, LocalDate today) {
        Map<String, Set<LocalDate>> weeksByKey = new HashMap<>();
        Map<String, List<Integer>> minutesByKey = new HashMap<>();
        Map<String, SessionDay> sample = new HashMap<>();
        for (SessionDay session : sessions) {
            if (session.kind() != SessionKind.SPORT || session.sport() == null || session.date() == null) {
                continue;
            }
            String key = session.sport().name() + "#" + session.date().getDayOfWeek().name();
            weeksByKey.computeIfAbsent(key, k -> new HashSet<>()).add(weekStart(session.date()));
            minutesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(session.minutes());
            sample.putIfAbsent(key, session);
        }
        LocalDate thisWeek = weekStart(today);
        List<Habit> habits = new ArrayList<>();
        for (Map.Entry<String, Set<LocalDate>> entry : weeksByKey.entrySet()) {
            Set<LocalDate> weeks = entry.getValue();
            LocalDate week = weeks.contains(thisWeek) ? thisWeek : thisWeek.minusWeeks(1);
            int run = 0;
            while (weeks.contains(week)) {
                run++;
                week = week.minusWeeks(1);
            }
            if (run >= MIN_WEEKS) {
                SessionDay one = sample.get(entry.getKey());
                habits.add(new Habit(one.date().getDayOfWeek(), one.sport(), median(minutesByKey.get(entry.getKey())), run));
            }
        }
        habits.sort(Comparator.comparingInt(Habit::weeks).reversed().thenComparing(Habit::weekday));
        return habits;
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static int median(List<Integer> values) {
        List<Integer> sorted = values.stream().filter(value -> value > 0).sorted().toList();
        return sorted.isEmpty() ? 0 : sorted.get((sorted.size() - 1) / 2);
    }
}
