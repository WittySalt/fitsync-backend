package com.fitsync.workout.progress;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.dto.progress.ChartPoint;
import com.fitsync.workout.dto.progress.Highlight;
import com.fitsync.workout.dto.progress.SeriesPoint;
import com.fitsync.workout.dto.progress.TrainingProgressView;
import com.fitsync.workout.dto.progress.TrainingProgressView.Activity;
import com.fitsync.workout.dto.progress.TrainingProgressView.ByKind;
import com.fitsync.workout.dto.progress.TrainingProgressView.CalendarDay;
import com.fitsync.workout.dto.progress.TrainingProgressView.Count;
import com.fitsync.workout.dto.progress.TrainingProgressView.Form;
import com.fitsync.workout.dto.progress.TrainingProgressView.Hero;
import com.fitsync.workout.dto.progress.TrainingProgressView.Lift;
import com.fitsync.workout.dto.progress.TrainingProgressView.Load;
import com.fitsync.workout.dto.progress.TrainingProgressView.LoadFigure;
import com.fitsync.workout.dto.progress.TrainingProgressView.LoadWeek;
import com.fitsync.workout.dto.progress.TrainingProgressView.MinutesPoint;
import com.fitsync.workout.dto.progress.TrainingProgressView.MuscleExercise;
import com.fitsync.workout.dto.progress.TrainingProgressView.MuscleGroupSets;
import com.fitsync.workout.dto.progress.TrainingProgressView.Muscles;
import com.fitsync.workout.dto.progress.TrainingProgressView.RecordEntry;
import com.fitsync.workout.dto.progress.TrainingProgressView.RecordsFigure;
import com.fitsync.workout.dto.progress.TrainingProgressView.UsualRange;
import com.fitsync.workout.dto.progress.TrainingProgressView.WeekValue;
import com.fitsync.workout.progress.ProgressFacts.ActivitySummary;
import com.fitsync.workout.progress.ProgressFacts.RecordEvent;
import com.fitsync.workout.progress.ProgressFacts.SessionExercise;

/** Builds the Training page of Progress from session snapshots. A pure function of its inputs. */
public final class TrainingProgressCalculator {

    /** The seven groups the catalogue tracks; FULL_BODY and CARDIO are not muscle groups here. */
    public static final List<MuscleGroup> GROUPS = List.of(MuscleGroup.CHEST, MuscleGroup.BACK,
            MuscleGroup.SHOULDERS, MuscleGroup.ARMS, MuscleGroup.CORE, MuscleGroup.GLUTES, MuscleGroup.LEGS);

    static final int USUAL_LOW = 10;
    static final int USUAL_HIGH = 20;
    static final double MUSCLE_UNDER_SETS = 6.0;
    static final double LIFT_UP_PCT = 5.0;
    static final int FITNESS_WEEKS = 6;
    static final double FORM_BAND = 0.15;
    static final int MAX_HIGHLIGHTS = 4;
    private static final double LOAD_LEVEL_PCT = 5.0;
    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final double DAYS_PER_WEEK = 7.0;

    private TrainingProgressCalculator() {
    }

    /**
     * Everything the calculation reads.
     *
     * @param sessions     completed sessions from at least six weeks before the previous period to the
     *                     end of the period, sorted by date then start; sets are only needed from the
     *                     previous period on
     * @param bestBefore   each exercise's best estimated single before the previous period
     * @param bodyWeightKg body weight for relative strength, or null for no ratios
     * @param plannedDays  the weekdays of the active plan, empty without one
     */
    public record Inputs(ProgressPeriod period, LocalDate today, List<SessionSnap> sessions,
                         Map<UUID, BigDecimal> bestBefore, BigDecimal bodyWeightKg, Set<DayOfWeek> plannedDays) {

        public Inputs {
            sessions = List.copyOf(sessions);
            bestBefore = Map.copyOf(bestBefore);
            plannedDays = plannedDays == null ? Set.of() : Set.copyOf(plannedDays);
        }

        @Override
        public List<SessionSnap> sessions() {
            return List.copyOf(sessions);
        }

        @Override
        public Map<UUID, BigDecimal> bestBefore() {
            return Map.copyOf(bestBefore);
        }

        @Override
        public Set<DayOfWeek> plannedDays() {
            return Set.copyOf(plannedDays);
        }
    }

    /** The parts the weekly card reuses: the main lifts and each activity's summary. */
    public record CardFacts(List<Lift> lifts, List<Activity> activities) {

        public CardFacts {
            lifts = List.copyOf(lifts);
            activities = List.copyOf(activities);
        }

        @Override
        public List<Lift> lifts() {
            return List.copyOf(lifts);
        }

        @Override
        public List<Activity> activities() {
            return List.copyOf(activities);
        }
    }

    public static TrainingProgressView compute(Inputs inputs) {
        ProgressPeriod period = inputs.period();
        ProgressPeriod previous = period.previous();
        List<SessionSnap> all = inputs.sessions();
        List<SessionSnap> inPeriod = within(all, period);
        List<SessionSnap> inPrevious = within(all, previous);

        List<RecordEvent> events = ProgressFacts.records(all, inputs.bestBefore(), previous.from());
        List<RecordEvent> periodEvents = events.stream().filter(e -> period.contains(e.date())).toList();
        long previousEvents = events.stream().filter(e -> previous.contains(e.date())).count();
        Set<SessionExercise> recordKeys = ProgressFacts.recordKeys(events);

        List<Lift> lifts = ProgressFacts.lifts(inPeriod, recordKeys, inputs.bodyWeightKg());
        List<Activity> activities = activities(all, inPeriod, inPrevious, period, inputs.today(), lifts);
        List<LoadWeek> loadWeeks = loadWeeks(all, previous.from(), period.to());
        Muscles muscles = muscles(inPeriod, period, inputs.today());

        Hero hero = new Hero(
                new Count(inPeriod.size(), inPrevious.size()),
                new Count(ProgressFacts.minutes(inPeriod), ProgressFacts.minutes(inPrevious)),
                new LoadFigure(load(inPeriod), load(inPrevious), estimatedShare(inPeriod)),
                new RecordsFigure(periodEvents.size(), (int) previousEvents,
                        new ArrayList<>(new LinkedHashSet<>(periodEvents.stream().map(RecordEvent::name).toList()))));

        List<Highlight> highlights = highlights(inputs, inPeriod, inPrevious, lifts, periodEvents, muscles,
                loadWeeks);

        return new TrainingProgressView(period.range(), previous.range(), hero, highlights, activities,
                load(all, loadWeeks, previous.from(), period.to()), muscles, lifts, calendar(inPeriod, recordKeys),
                recordEntries(periodEvents));
    }

    /** The main lifts and the activity summaries without the rest of the page, for the weekly card. */
    public static CardFacts cardFacts(Inputs inputs) {
        ProgressPeriod period = inputs.period();
        List<SessionSnap> all = inputs.sessions();
        List<SessionSnap> inPeriod = within(all, period);
        List<RecordEvent> events = ProgressFacts.records(all, inputs.bestBefore(), period.previous().from());
        List<Lift> lifts = ProgressFacts.lifts(inPeriod, ProgressFacts.recordKeys(events), inputs.bodyWeightKg());
        return new CardFacts(lifts, activities(all, inPeriod, within(all, period.previous()), period,
                inputs.today(), lifts));
    }

    static List<SessionSnap> within(List<SessionSnap> sessions, ProgressPeriod period) {
        return sessions.stream().filter(s -> period.contains(s.date())).toList();
    }

    // ------------------------------------------------------------------ activities

    private static List<Activity> activities(List<SessionSnap> all, List<SessionSnap> inPeriod,
                                             List<SessionSnap> inPrevious, ProgressPeriod period, LocalDate today,
                                             List<Lift> lifts) {
        Map<String, ActivityKey> keys = new LinkedHashMap<>();
        for (SessionSnap session : inPeriod) {
            ActivityKey key = ProgressFacts.keyOf(session);
            keys.putIfAbsent(key.key(), key);
        }
        List<Activity> activities = new ArrayList<>();
        for (ActivityKey key : keys.values()) {
            List<SessionSnap> mine = ProgressFacts.ofActivity(inPeriod, key.key());
            List<SessionSnap> before = ProgressFacts.ofActivity(inPrevious, key.key());
            String name = ProgressFacts.activityName(key, mine);
            ActivitySummary summary = ProgressFacts.summarise(key, name, mine, before,
                    ProgressFacts.ofActivity(all, key.key()), period, today, lifts);
            List<MinutesPoint> cumulative = new ArrayList<>();
            int running = 0;
            for (SessionSnap session : mine) {
                running += session.minutes();
                cumulative.add(new MinutesPoint(session.date(), running));
            }
            activities.add(new Activity(key.key(), name, mine.size(), running, cumulative, summary.lines(),
                    summary.context()));
        }
        activities.sort(Comparator.<Activity>comparingInt(a -> ProgressFacts.kindRank(ActivityKey.parse(a.key()).kind()))
                .thenComparingInt(a -> -a.minutes())
                .thenComparing(Activity::name));
        return activities;
    }

    // ------------------------------------------------------------------ load

    static long load(List<SessionSnap> sessions) {
        long total = 0;
        for (SessionSnap session : sessions) {
            total += session.load();
        }
        return total;
    }

    static BigDecimal estimatedShare(List<SessionSnap> sessions) {
        long total = load(sessions);
        if (total == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        long estimated = 0;
        for (SessionSnap session : sessions) {
            if (session.loadEstimated()) {
                estimated += session.load();
            }
        }
        return BigDecimal.valueOf(estimated).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    /** Monday weeks from the week of {@code from} to the week of {@code to}. */
    static List<LoadWeek> loadWeeks(List<SessionSnap> sessions, LocalDate from, LocalDate to) {
        Map<LocalDate, long[]> byWeek = new TreeMap<>();
        Set<LocalDate> estimated = new HashSet<>();
        for (SessionSnap session : sessions) {
            LocalDate week = ProgressPeriod.mondayOf(session.date());
            long[] parts = byWeek.computeIfAbsent(week, w -> new long[SessionKind.values().length]);
            parts[session.kind().ordinal()] += session.load();
            if (session.loadEstimated() && session.load() > 0) {
                estimated.add(week);
            }
        }
        List<LoadWeek> weeks = new ArrayList<>();
        for (LocalDate week = ProgressPeriod.mondayOf(from); !week.isAfter(to); week = week.plusWeeks(1)) {
            long[] parts = byWeek.getOrDefault(week, new long[SessionKind.values().length]);
            ByKind byKind = new ByKind(parts[SessionKind.STRENGTH.ordinal()], parts[SessionKind.CARDIO.ordinal()],
                    parts[SessionKind.SPORT.ordinal()], parts[SessionKind.MOBILITY.ordinal()]);
            long total = 0;
            for (long part : parts) {
                total += part;
            }
            weeks.add(new LoadWeek(week, byKind, total, estimated.contains(week)));
        }
        return weeks;
    }

    /**
     * Fitness is the mean weekly load of the six weeks ending with each week, fatigue that week's load,
     * and form compares them in the last week: tired when fatigue is more than 15% over fitness,
     * fresh when it is more than 15% under, balanced otherwise.
     */
    private static Load load(List<SessionSnap> all, List<LoadWeek> weeks, LocalDate from, LocalDate to) {
        Map<LocalDate, Long> totals = new HashMap<>();
        for (LoadWeek week : loadWeeks(all, ProgressPeriod.mondayOf(from).minusWeeks(FITNESS_WEEKS - 1L), to)) {
            totals.put(week.weekStart(), week.total());
        }
        List<WeekValue> fitness = new ArrayList<>();
        List<WeekValue> fatigue = new ArrayList<>();
        for (LoadWeek week : weeks) {
            fitness.add(new WeekValue(week.weekStart(), fitnessAt(totals, week.weekStart())));
            fatigue.add(new WeekValue(week.weekStart(), week.total()));
        }
        Form form = Form.BALANCED;
        if (!weeks.isEmpty()) {
            form = form(fitness.get(fitness.size() - 1).value(), fatigue.get(fatigue.size() - 1).value());
        }
        return new Load(weeks, fitness, fatigue, form);
    }

    static long fitnessAt(Map<LocalDate, Long> totals, LocalDate weekStart) {
        long sum = 0;
        for (int i = 0; i < FITNESS_WEEKS; i++) {
            sum += totals.getOrDefault(weekStart.minusWeeks(i), 0L);
        }
        return Math.round(sum / (double) FITNESS_WEEKS);
    }

    static Form form(long fitness, long fatigue) {
        if (fitness <= 0) {
            return fatigue > 0 ? Form.TIRED : Form.BALANCED;
        }
        double relative = (fitness - fatigue) / (double) fitness;
        if (relative < -FORM_BAND) {
            return Form.TIRED;
        }
        if (relative > FORM_BAND) {
            return Form.FRESH;
        }
        return Form.BALANCED;
    }

    // ------------------------------------------------------------------ muscles

    /**
     * Hard sets a week per group: a set with a logged weight or reps counts 1 for the exercise's primary
     * group and 0.5 for each secondary group, averaged over the weeks of the period that have happened.
     */
    static Muscles muscles(List<SessionSnap> inPeriod, ProgressPeriod period, LocalDate today) {
        long days = Math.max(1, ProgressFacts.elapsedDays(period, today));
        BigDecimal weeks = BigDecimal.valueOf(days).divide(BigDecimal.valueOf(DAYS_PER_WEEK), 6, RoundingMode.HALF_UP);
        Map<MuscleGroup, BigDecimal> totals = new EnumMap<>(MuscleGroup.class);
        Map<MuscleGroup, Map<UUID, BigDecimal>> byExercise = new EnumMap<>(MuscleGroup.class);
        Map<UUID, String> names = new HashMap<>();
        for (SessionSnap session : inPeriod) {
            for (ExerciseSnap exercise : session.exercises()) {
                long hardSets = exercise.sets().stream().filter(SetSnap::hasWork).count();
                if (hardSets == 0) {
                    continue;
                }
                names.put(exercise.exerciseId(), exercise.name());
                BigDecimal count = BigDecimal.valueOf(hardSets);
                if (GROUPS.contains(exercise.primary())) {
                    credit(totals, byExercise, exercise.primary(), exercise.exerciseId(), count);
                }
                for (MuscleGroup secondary : exercise.secondary()) {
                    if (GROUPS.contains(secondary) && secondary != exercise.primary()) {
                        credit(totals, byExercise, secondary, exercise.exerciseId(), count.multiply(HALF));
                    }
                }
            }
        }
        List<MuscleGroupSets> groups = new ArrayList<>();
        for (MuscleGroup group : GROUPS) {
            BigDecimal total = totals.getOrDefault(group, BigDecimal.ZERO);
            List<MuscleExercise> exercises = new ArrayList<>();
            byExercise.getOrDefault(group, Map.of()).forEach((id, sets) ->
                    exercises.add(new MuscleExercise(id, names.get(id), sets.setScale(1, RoundingMode.HALF_UP))));
            exercises.sort(Comparator.comparing(MuscleExercise::sets).reversed().thenComparing(MuscleExercise::name));
            groups.add(new MuscleGroupSets(group, total.divide(weeks, 1, RoundingMode.HALF_UP), exercises));
        }
        return new Muscles(weeks.setScale(1, RoundingMode.HALF_UP), groups, new UsualRange(USUAL_LOW, USUAL_HIGH));
    }

    private static void credit(Map<MuscleGroup, BigDecimal> totals, Map<MuscleGroup, Map<UUID, BigDecimal>> byExercise,
                               MuscleGroup group, UUID exerciseId, BigDecimal amount) {
        totals.merge(group, amount, BigDecimal::add);
        byExercise.computeIfAbsent(group, g -> new LinkedHashMap<>()).merge(exerciseId, amount, BigDecimal::add);
    }

    // ------------------------------------------------------------------ calendar and records

    static List<CalendarDay> calendar(List<SessionSnap> inPeriod, Set<SessionExercise> recordKeys) {
        Map<LocalDate, List<SessionSnap>> byDate = new TreeMap<>();
        for (SessionSnap session : inPeriod) {
            byDate.computeIfAbsent(session.date(), d -> new ArrayList<>()).add(session);
        }
        List<CalendarDay> days = new ArrayList<>();
        for (Map.Entry<LocalDate, List<SessionSnap>> entry : byDate.entrySet()) {
            Map<SessionKind, Integer> minutesByKind = new EnumMap<>(SessionKind.class);
            int minutes = 0;
            boolean record = false;
            for (SessionSnap session : entry.getValue()) {
                minutesByKind.merge(session.kind(), session.minutes(), Integer::sum);
                minutes += session.minutes();
                for (ExerciseSnap exercise : session.exercises()) {
                    record |= recordKeys.contains(new SessionExercise(session.sessionId(), exercise.exerciseId()));
                }
            }
            SessionKind kind = entry.getValue().get(0).kind();
            int most = -1;
            for (Map.Entry<SessionKind, Integer> kindMinutes : minutesByKind.entrySet()) {
                if (kindMinutes.getValue() > most) {
                    most = kindMinutes.getValue();
                    kind = kindMinutes.getKey();
                }
            }
            days.add(new CalendarDay(entry.getKey(), kind, minutes, record));
        }
        return days;
    }

    static List<RecordEntry> recordEntries(List<RecordEvent> periodEvents) {
        List<RecordEntry> entries = new ArrayList<>();
        for (int i = periodEvents.size() - 1; i >= 0; i--) {
            RecordEvent event = periodEvents.get(i);
            entries.add(new RecordEntry(event.date(), event.exerciseId(), event.name(), event.value(), "kg",
                    event.delta()));
        }
        return entries;
    }

    // ------------------------------------------------------------------ highlights

    /** The rules in order of importance; at most four fire. */
    static List<Highlight> highlights(Inputs inputs, List<SessionSnap> inPeriod, List<SessionSnap> inPrevious,
                                      List<Lift> lifts, List<RecordEvent> periodEvents, Muscles muscles,
                                      List<LoadWeek> loadWeeks) {
        ProgressPeriod period = inputs.period();
        List<Highlight> found = new ArrayList<>();
        addIfPresent(found, liftUp(period, lifts, periodEvents));
        addIfPresent(found, paceFaster(inPeriod));
        addIfPresent(found, muscleUnder(muscles));
        addIfPresent(found, loadTrend(period, inputs.today(), inPeriod, inPrevious, loadWeeks));
        addIfPresent(found, consistency(period, inputs.today(), inPeriod, inputs.plannedDays()));
        return found.size() > MAX_HIGHLIGHTS ? found.subList(0, MAX_HIGHLIGHTS) : found;
    }

    private static void addIfPresent(List<Highlight> found, Highlight highlight) {
        if (highlight != null) {
            found.add(highlight);
        }
    }

    /** LIFT_UP: the lift whose estimated best single rose the most, when that is more than 5%. */
    static Highlight liftUp(ProgressPeriod period, List<Lift> lifts, List<RecordEvent> periodEvents) {
        Lift best = null;
        double bestPct = LIFT_UP_PCT;
        for (Lift lift : lifts) {
            List<SeriesPoint> points = lift.e1rm();
            double pct = StrengthMath.rawPct(points.get(0).value(), points.get(points.size() - 1).value());
            if (pct > bestPct) {
                bestPct = pct;
                best = lift;
            }
        }
        if (best == null) {
            return null;
        }
        List<SeriesPoint> points = best.e1rm();
        SeriesPoint first = points.get(0);
        SeriesPoint last = points.get(points.size() - 1);
        UUID exerciseId = best.exerciseId();
        long records = periodEvents.stream().filter(e -> e.exerciseId().equals(exerciseId)).count();
        String phrase = ProgressText.periodPhrase(period.days());
        String title = "Your " + best.name().toLowerCase(Locale.ROOT) + "'s estimated best single is up "
                + best.changePct() + "% in " + phrase + ".";
        String context = ProgressText.signedKg(last.value().subtract(first.value())) + " compared with "
                + ProgressText.date(first.date()) + ", " + records + (records == 1 ? " record " : " records ")
                + recordsPhrase(period.days());
        return new Highlight("LIFT_UP", title, points.stream()
                .map(p -> new ChartPoint(p.date().toString(), p.value())).toList(), "kg", context);
    }

    private static String recordsPhrase(long days) {
        if (days == 7) {
            return "in this week";
        }
        if (days % 7 == 0 && days / 7 <= 8) {
            return "in these " + ProgressText.periodPhrase(days);
        }
        return "in this period";
    }

    /** PACE_FASTER: the change in pace across the period's cardio sessions of the most logged kind of cardio. */
    static Highlight paceFaster(List<SessionSnap> inPeriod) {
        List<SessionSnap> paced = inPeriod.stream()
                .filter(s -> s.kind() == SessionKind.CARDIO && s.paceSecPerKm() != null).toList();
        if (paced.size() < 2) {
            return null;
        }
        String name = ProgressFacts.mostLoggedName(paced, "Cardio");
        List<SessionSnap> same = paced.stream()
                .filter(s -> name.equals(ProgressFacts.firstExerciseName(s)) || s.exercises().isEmpty()
                        && "Cardio".equals(name))
                .toList();
        if (same.size() < 2) {
            return null;
        }
        int first = same.get(0).paceSecPerKm();
        int last = same.get(same.size() - 1).paceSecPerKm();
        int change = first - last;
        if (change == 0) {
            return null;
        }
        String direction = change > 0 ? "faster" : "slower";
        int seconds = Math.abs(change);
        String title = ProgressFacts.isRunning(name)
                ? "You run a kilometre " + seconds + " seconds " + direction + "."
                : "Your kilometre is " + seconds + " seconds " + direction + ".";
        String context = name + ", " + seconds + " seconds a km " + direction + " than on "
                + ProgressText.date(same.get(0).date());
        return new Highlight("PACE_FASTER", title, same.stream()
                .map(s -> new ChartPoint(s.date().toString(), BigDecimal.valueOf(s.paceSecPerKm()))).toList(),
                "sec/km", context);
    }

    /** MUSCLE_UNDER: the group with the fewest hard sets a week, when that is under six and strength was trained. */
    static Highlight muscleUnder(Muscles muscles) {
        BigDecimal sum = BigDecimal.ZERO;
        MuscleGroupSets fewest = null;
        for (MuscleGroupSets group : muscles.groups()) {
            sum = sum.add(group.setsPerWeek());
            if (fewest == null || group.setsPerWeek().compareTo(fewest.setsPerWeek()) < 0) {
                fewest = group;
            }
        }
        if (fewest == null || sum.signum() == 0 || fewest.setsPerWeek().doubleValue() >= MUSCLE_UNDER_SETS) {
            return null;
        }
        String title = groupName(fewest.group()) + " had " + ProgressText.kg(fewest.setsPerWeek())
                + " hard sets a week, the fewest of any group.";
        return new Highlight("MUSCLE_UNDER", title, muscles.groups().stream()
                .map(g -> new ChartPoint(groupName(g.group()), g.setsPerWeek())).toList(), "sets a week",
                "A usual range is " + USUAL_LOW + " to " + USUAL_HIGH + " hard sets a week, shaded green");
    }

    static String groupName(MuscleGroup group) {
        String lower = group.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    /**
     * LOAD_TREND: load against the previous period, worded from the run of the last finished weeks and
     * how the week in progress compares with the last finished one.
     */
    static Highlight loadTrend(ProgressPeriod period, LocalDate today, List<SessionSnap> inPeriod,
                               List<SessionSnap> inPrevious, List<LoadWeek> loadWeeks) {
        long now = load(inPeriod);
        long before = load(inPrevious);
        if (now == 0 || before == 0) {
            return null;
        }
        int pct = StrengthMath.pctChange(BigDecimal.valueOf(before), BigDecimal.valueOf(now));
        LocalDate firstWeek = ProgressPeriod.mondayOf(period.from());
        List<LoadWeek> periodWeeks = loadWeeks.stream().filter(w -> !w.weekStart().isBefore(firstWeek)).toList();
        LocalDate currentWeek = ProgressPeriod.mondayOf(today);
        List<LoadWeek> finished = new ArrayList<>();
        LoadWeek inProgress = null;
        for (LoadWeek week : loadWeeks) {
            if (week.weekStart().plusDays(6).isBefore(today)) {
                finished.add(week);
            } else if (week.weekStart().equals(currentWeek) && !week.weekStart().isBefore(firstWeek)) {
                inProgress = week;
            }
        }
        int rising = run(finished, true);
        int falling = run(finished, false);
        String label = ProgressText.previousLabel(period.days());
        String head;
        if (rising >= 2) {
            head = "Load rose for " + ProgressText.word(rising) + " weeks";
        } else if (falling >= 2) {
            head = "Load fell for " + ProgressText.word(falling) + " weeks";
        } else if (pct > LOAD_LEVEL_PCT) {
            head = "Load is up " + pct + "% on " + label;
        } else if (pct < -LOAD_LEVEL_PCT) {
            head = "Load is down " + Math.abs(pct) + "% on " + label;
        } else {
            head = "Load held about level";
        }
        String tail = ".";
        if (inProgress != null && !finished.isEmpty()) {
            long lastFinished = finished.get(finished.size() - 1).total();
            if (inProgress.total() < lastFinished) {
                tail = ", and this week is lighter so far.";
            } else if (inProgress.total() > lastFinished) {
                tail = ", and this week is heavier so far.";
            }
        }
        return new Highlight("LOAD_TREND", head + tail, periodWeeks.stream()
                .map(w -> new ChartPoint(w.weekStart().toString(), BigDecimal.valueOf(w.total()))).toList(),
                "effort × min", ProgressText.signedPct(pct) + " on " + label + ", effort × minutes each week");
    }

    /** How many of the last weeks in a row each rose (or fell) against the week before. */
    static int run(List<LoadWeek> weeks, boolean rising) {
        int count = 0;
        for (int i = weeks.size() - 1; i > 0; i--) {
            long now = weeks.get(i).total();
            long before = weeks.get(i - 1).total();
            if (rising ? now > before : now < before) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    /** CONSISTENCY: days trained on the active plan's weekdays against the planned days so far. */
    static Highlight consistency(ProgressPeriod period, LocalDate today, List<SessionSnap> inPeriod,
                                 Set<DayOfWeek> plannedDays) {
        if (plannedDays.isEmpty()) {
            return null;
        }
        LocalDate end = period.to().isAfter(today) ? today : period.to();
        Set<LocalDate> trained = new HashSet<>();
        for (SessionSnap session : inPeriod) {
            trained.add(session.date());
        }
        int planned = 0;
        int hit = 0;
        for (LocalDate day = period.from(); !day.isAfter(end); day = day.plusDays(1)) {
            if (plannedDays.contains(day.getDayOfWeek())) {
                planned++;
                if (trained.contains(day)) {
                    hit++;
                }
            }
        }
        if (planned == 0) {
            return null;
        }
        int extra = trained.size() - hit;
        String title = "You trained on " + hit + " of " + planned + " planned days"
                + (extra > 0 ? ", and on " + extra + (extra == 1 ? " day" : " days") + " besides." : ".");
        Map<LocalDate, Integer> perWeek = new TreeMap<>();
        for (LocalDate week = ProgressPeriod.mondayOf(period.from()); !week.isAfter(end); week = week.plusWeeks(1)) {
            perWeek.put(week, 0);
        }
        for (LocalDate day : trained) {
            perWeek.merge(ProgressPeriod.mondayOf(day), 1, Integer::sum);
        }
        List<ChartPoint> series = new ArrayList<>();
        perWeek.forEach((week, count) -> series.add(new ChartPoint(week.toString(), BigDecimal.valueOf(count))));
        return new Highlight("CONSISTENCY", title, series, "days",
                plannedDays.size() + (plannedDays.size() == 1 ? " planned day" : " planned days")
                        + " a week in your active plan");
    }
}
