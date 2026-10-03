package com.fitsync.workout.progress;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fitsync.workout.dto.progress.ActivityProgressView;
import com.fitsync.workout.dto.progress.ActivityProgressView.Effort;
import com.fitsync.workout.dto.progress.ActivityProgressView.Longest;
import com.fitsync.workout.dto.progress.ActivityProgressView.Session;
import com.fitsync.workout.dto.progress.ActivityProgressView.Tiles;
import com.fitsync.workout.dto.progress.ActivityProgressView.UsualTime;
import com.fitsync.workout.dto.progress.ActivityProgressView.Week;
import com.fitsync.workout.dto.progress.ActivityProgressView.WhenCell;
import com.fitsync.workout.dto.progress.TrainingProgressView.Lift;
import com.fitsync.workout.progress.ProgressFacts.ActivitySummary;

/** Builds the page of one activity from session snapshots. A pure function of its inputs. */
public final class ActivityProgressCalculator {

    static final int MIN_SESSIONS_FOR_USUAL_TIME = 3;

    private ActivityProgressCalculator() {
    }

    /**
     * @param sessions all loaded sessions (any activity), sorted by date then start; they reach back far
     *                 enough for the run of weeks, and carry sets from the previous period on
     * @param bestBefore each exercise's best estimated single before the previous period, for strength
     */
    public static ActivityProgressView compute(ActivityKey key, ProgressPeriod period, LocalDate today,
                                               List<SessionSnap> sessions,
                                               Map<java.util.UUID, BigDecimal> bestBefore) {
        ProgressPeriod previous = period.previous();
        List<SessionSnap> mine = ProgressFacts.ofActivity(sessions, key.key());
        List<SessionSnap> inPeriod = TrainingProgressCalculator.within(mine, period);
        List<SessionSnap> inPrevious = TrainingProgressCalculator.within(mine, previous);
        String name = ProgressFacts.activityName(key, inPeriod.isEmpty() ? mine : inPeriod);

        List<Lift> lifts = List.of();
        if (key.kind() == com.fitsync.workout.domain.SessionKind.STRENGTH) {
            List<ProgressFacts.RecordEvent> events = ProgressFacts.records(sessions, bestBefore, previous.from());
            lifts = ProgressFacts.lifts(inPeriod, ProgressFacts.recordKeys(events), null);
        }
        ActivitySummary summary = ProgressFacts.summarise(key, name, inPeriod, inPrevious, mine, period, today, lifts);
        LocalDate endDate = period.to().isAfter(today) ? today : period.to();

        Tiles tiles = new Tiles((int) ProgressFacts.minutes(inPeriod), inPeriod.size(),
                ProgressFacts.weeksInARow(mine, endDate), longest(inPeriod), usualTime(inPeriod), effort(inPeriod));

        return new ActivityProgressView(key.key(), name, key.kind(), period.range(), previous.range(), tiles,
                weekly(mine, previous.from(), period.to(), today), sessionsNewestFirst(inPeriod), whenGrid(inPeriod),
                summary.lines(), summary.context());
    }

    static Longest longest(List<SessionSnap> sessions) {
        SessionSnap longest = null;
        for (SessionSnap session : sessions) {
            if (longest == null || session.minutes() >= longest.minutes()) {
                longest = session;
            }
        }
        return longest == null ? null : new Longest(longest.minutes(), longest.date());
    }

    /** The weekday most sessions fell on (the earlier weekday on a tie) and the median start hour on it. */
    static UsualTime usualTime(List<SessionSnap> sessions) {
        if (sessions.size() < MIN_SESSIONS_FOR_USUAL_TIME) {
            return null;
        }
        Map<DayOfWeek, List<Integer>> hours = new EnumMap<>(DayOfWeek.class);
        for (SessionSnap session : sessions) {
            hours.computeIfAbsent(session.localStart().getDayOfWeek(), d -> new ArrayList<>())
                    .add(session.localStart().getHour());
        }
        DayOfWeek best = null;
        for (Map.Entry<DayOfWeek, List<Integer>> entry : hours.entrySet()) {
            if (best == null || entry.getValue().size() > hours.get(best).size()) {
                best = entry.getKey();
            }
        }
        List<Integer> onDay = new ArrayList<>(hours.get(best));
        onDay.sort(Integer::compare);
        return new UsualTime(best, onDay.get((onDay.size() - 1) / 2));
    }

    static Effort effort(List<SessionSnap> sessions) {
        List<Integer> efforts = sessions.stream().map(SessionSnap::effort).filter(e -> e != null).toList();
        if (efforts.isEmpty()) {
            return null;
        }
        int sum = 0;
        for (int value : efforts) {
            sum += value;
        }
        BigDecimal average = BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(efforts.size()), 1, RoundingMode.HALF_UP);
        return new Effort(average, efforts.get(0), efforts.get(efforts.size() - 1));
    }

    static List<Week> weekly(List<SessionSnap> mine, LocalDate from, LocalDate to, LocalDate today) {
        Map<LocalDate, int[]> byWeek = new TreeMap<>();
        for (SessionSnap session : mine) {
            int[] figures = byWeek.computeIfAbsent(ProgressPeriod.mondayOf(session.date()), w -> new int[2]);
            figures[0] += session.minutes();
            figures[1]++;
        }
        LocalDate current = ProgressPeriod.mondayOf(today);
        List<Week> weeks = new ArrayList<>();
        for (LocalDate week = ProgressPeriod.mondayOf(from); !week.isAfter(to); week = week.plusWeeks(1)) {
            int[] figures = byWeek.getOrDefault(week, new int[2]);
            weeks.add(new Week(week, figures[0], figures[1], week.equals(current)));
        }
        return weeks;
    }

    static List<Session> sessionsNewestFirst(List<SessionSnap> sessions) {
        List<Session> out = new ArrayList<>();
        for (int i = sessions.size() - 1; i >= 0; i--) {
            SessionSnap session = sessions.get(i);
            out.add(new Session(session.sessionId(), session.startedAt(), session.minutes(), session.effort(),
                    session.effort() != null && session.effortEstimated(), session.calories(), session.distanceKm(),
                    session.paceSecPerKm()));
        }
        return out;
    }

    static List<WhenCell> whenGrid(List<SessionSnap> sessions) {
        Map<Integer, Integer> counts = new TreeMap<>();
        for (SessionSnap session : sessions) {
            int cell = session.localStart().getDayOfWeek().getValue() * 100 + session.localStart().getHour();
            counts.merge(cell, 1, Integer::sum);
        }
        List<WhenCell> cells = new ArrayList<>();
        counts.forEach((cell, count) -> cells.add(new WhenCell(DayOfWeek.of(cell / 100), cell % 100, count)));
        return cells;
    }
}
