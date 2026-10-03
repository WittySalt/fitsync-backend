package com.fitsync.workout.progress;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.dto.progress.ExerciseProgressView;
import com.fitsync.workout.dto.progress.ExerciseProgressView.E1rmTile;
import com.fitsync.workout.dto.progress.ExerciseProgressView.HistoryEntry;
import com.fitsync.workout.dto.progress.ExerciseProgressView.LoggedSet;
import com.fitsync.workout.dto.progress.ExerciseProgressView.Series;
import com.fitsync.workout.dto.progress.ExerciseProgressView.SetFigure;
import com.fitsync.workout.dto.progress.ExerciseProgressView.Tiles;
import com.fitsync.workout.dto.progress.ExerciseProgressView.VolumeFigure;
import com.fitsync.workout.dto.progress.SeriesPoint;
import com.fitsync.workout.dto.progress.SummaryLine;
import com.fitsync.workout.dto.progress.Trend;
import com.fitsync.workout.progress.ProgressFacts.RecordEvent;
import com.fitsync.workout.progress.ProgressFacts.SessionExercise;

/** Builds the page of one exercise from its whole history. A pure function of its inputs. */
public final class ExerciseProgressCalculator {

    private static final double E1RM_TREND_PCT = 2.0;

    private ExerciseProgressCalculator() {
    }

    /** What the catalogue says about the exercise. */
    public record ExerciseInfo(UUID exerciseId, String name, MuscleGroup primaryGroup,
                               List<MuscleGroup> secondaryGroups, ExerciseEquipment equipment) {

        public ExerciseInfo {
            secondaryGroups = secondaryGroups == null ? List.of() : List.copyOf(secondaryGroups);
        }

        @Override
        public List<MuscleGroup> secondaryGroups() {
            return List.copyOf(secondaryGroups);
        }
    }

    /**
     * @param history every completed session of the caller with this exercise, sorted by date then start,
     *                each holding one exercise snapshot with all its performed sets
     */
    public static ExerciseProgressView compute(ExerciseInfo info, ProgressPeriod period, List<SessionSnap> history) {
        ProgressPeriod previous = period.previous();
        List<RecordEvent> events = ProgressFacts.records(history, Map.of(), history.isEmpty()
                ? period.from() : history.get(0).date());
        Set<SessionExercise> recordKeys = ProgressFacts.recordKeys(events);
        List<SessionSnap> inPeriod = TrainingProgressCalculator.within(history, period);
        List<SessionSnap> inPrevious = TrainingProgressCalculator.within(history, previous);

        List<SeriesPoint> e1rm = new ArrayList<>();
        List<SeriesPoint> heaviest = new ArrayList<>();
        List<SeriesPoint> volume = new ArrayList<>();
        List<SeriesPoint> reps = new ArrayList<>();
        SetFigure heaviestSet = null;
        VolumeFigure bestVolume = null;
        for (SessionSnap session : inPeriod) {
            ExerciseSnap exercise = session.exercises().get(0);
            boolean record = recordKeys.contains(new SessionExercise(session.sessionId(), exercise.exerciseId()));
            BigDecimal value = exercise.e1rm();
            if (value != null) {
                e1rm.add(new SeriesPoint(session.date(), StrengthMath.round1(value), record));
            }
            SetSnap top = heaviest(exercise);
            if (top != null) {
                heaviest.add(new SeriesPoint(session.date(), StrengthMath.round1(top.weightKg()), false));
                if (heaviestSet == null || top.weightKg().compareTo(heaviestSet.weightKg()) >= 0) {
                    heaviestSet = new SetFigure(StrengthMath.round1(top.weightKg()),
                            top.reps() == null ? 0 : top.reps(), session.date());
                }
            }
            BigDecimal sessionVolume = exercise.volume();
            if (sessionVolume.signum() > 0) {
                volume.add(new SeriesPoint(session.date(), StrengthMath.round1(sessionVolume), false));
                if (bestVolume == null || sessionVolume.compareTo(bestVolume.kg()) >= 0) {
                    bestVolume = new VolumeFigure(StrengthMath.round1(sessionVolume), session.date());
                }
            }
            int totalReps = totalReps(exercise);
            if (totalReps > 0) {
                reps.add(new SeriesPoint(session.date(), BigDecimal.valueOf(totalReps), false));
            }
        }

        E1rmTile e1rmTile = null;
        if (!e1rm.isEmpty()) {
            SeriesPoint first = e1rm.get(0);
            SeriesPoint last = e1rm.get(e1rm.size() - 1);
            e1rmTile = new E1rmTile(last.value(), last.value().subtract(first.value()), first.date());
        }
        int recordsInPeriod = (int) events.stream().filter(e -> period.contains(e.date())).count();
        Tiles tiles = new Tiles(e1rmTile, heaviestSet, bestVolume, events.size(), recordsInPeriod);

        List<HistoryEntry> entries = new ArrayList<>();
        for (int i = inPeriod.size() - 1; i >= 0; i--) {
            SessionSnap session = inPeriod.get(i);
            ExerciseSnap exercise = session.exercises().get(0);
            List<LoggedSet> sets = exercise.sets().stream()
                    .map(s -> new LoggedSet(StrengthMath.round1(s.weightKg()), s.reps())).toList();
            entries.add(new HistoryEntry(session.sessionId(), session.date(), sets,
                    recordKeys.contains(new SessionExercise(session.sessionId(), exercise.exerciseId()))));
        }

        return new ExerciseProgressView(info.exerciseId(), info.name(), info.primaryGroup(), info.secondaryGroups(),
                info.equipment(), history.isEmpty() ? null : history.get(0).date(), history.size(), period.range(),
                tiles, new Series(e1rm, heaviest, volume, reps), entries,
                summary(e1rm, reps, heaviestSet, inPeriod, inPrevious, period));
    }

    /** The heaviest set by weight; more reps break a tie. */
    static SetSnap heaviest(ExerciseSnap exercise) {
        SetSnap top = null;
        for (SetSnap set : exercise.sets()) {
            if (set.weightKg() == null || set.weightKg().signum() <= 0) {
                continue;
            }
            if (top == null || set.weightKg().compareTo(top.weightKg()) > 0
                    || set.weightKg().compareTo(top.weightKg()) == 0 && reps(set) > reps(top)) {
                top = set;
            }
        }
        return top;
    }

    private static int reps(SetSnap set) {
        return set.reps() == null ? 0 : set.reps();
    }

    private static int totalReps(ExerciseSnap exercise) {
        int total = 0;
        for (SetSnap set : exercise.sets()) {
            total += reps(set);
        }
        return total;
    }

    private static List<SummaryLine> summary(List<SeriesPoint> e1rm, List<SeriesPoint> reps, SetFigure heaviestSet,
                                             List<SessionSnap> inPeriod, List<SessionSnap> inPrevious,
                                             ProgressPeriod period) {
        List<SummaryLine> lines = new ArrayList<>();
        if (e1rm.size() >= 2) {
            BigDecimal first = e1rm.get(0).value();
            BigDecimal last = e1rm.get(e1rm.size() - 1).value();
            double pct = StrengthMath.rawPct(first, last);
            Trend trend = pct > E1RM_TREND_PCT ? Trend.UP : (pct < -E1RM_TREND_PCT ? Trend.DOWN : Trend.STEADY);
            lines.add(ProgressFacts.line("e1RM " + ProgressText.kg(first) + " → " + ProgressText.kg(last) + " kg",
                    "estimated best single", trend));
        } else if (e1rm.size() == 1) {
            lines.add(ProgressFacts.line("e1RM " + ProgressText.kg(e1rm.get(0).value()) + " kg",
                    "estimated best single", Trend.STEADY));
        } else if (!reps.isEmpty()) {
            BigDecimal first = reps.get(0).value();
            BigDecimal last = reps.get(reps.size() - 1).value();
            lines.add(ProgressFacts.line("Reps " + first + " → " + last, "a session",
                    ProgressFacts.trend(last.doubleValue(), first.doubleValue(), 0.05)));
        }
        if (heaviestSet != null) {
            BigDecimal before = null;
            for (SessionSnap session : inPrevious) {
                SetSnap top = heaviest(session.exercises().get(0));
                if (top != null && (before == null || top.weightKg().compareTo(before) > 0)) {
                    before = top.weightKg();
                }
            }
            String figure = "Heaviest set " + ProgressText.kg(heaviestSet.weightKg()) + " kg × " + heaviestSet.reps();
            if (before == null) {
                lines.add(ProgressFacts.line(figure, "on " + ProgressText.date(heaviestSet.date()), Trend.STEADY));
            } else {
                int compare = heaviestSet.weightKg().compareTo(StrengthMath.round1(before));
                Trend trend = compare > 0 ? Trend.UP : (compare < 0 ? Trend.DOWN : Trend.STEADY);
                lines.add(ProgressFacts.line(figure, "against " + ProgressText.kg(before) + " kg before", trend));
            }
        }
        lines.add(ProgressFacts.line(ProgressText.sessions(inPeriod.size()),
                "against " + inPrevious.size() + " in " + ProgressText.previousLabel(period.days()),
                ProgressFacts.trend(inPeriod.size(), inPrevious.size(), 0.10)));
        return lines;
    }
}
