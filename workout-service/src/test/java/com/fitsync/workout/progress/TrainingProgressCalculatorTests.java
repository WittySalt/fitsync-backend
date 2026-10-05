package com.fitsync.workout.progress;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.dto.progress.Highlight;
import com.fitsync.workout.dto.progress.SummaryLine;
import com.fitsync.workout.dto.progress.Trend;
import com.fitsync.workout.dto.progress.TrainingProgressView;
import com.fitsync.workout.dto.progress.TrainingProgressView.Activity;
import com.fitsync.workout.dto.progress.TrainingProgressView.Form;
import com.fitsync.workout.dto.progress.TrainingProgressView.Level;
import com.fitsync.workout.dto.progress.TrainingProgressView.Lift;
import com.fitsync.workout.dto.progress.TrainingProgressView.MuscleGroupSets;
import com.fitsync.workout.dto.progress.TrainingProgressView.Muscles;
import com.fitsync.workout.dto.progress.TrainingProgressView.UsualRange;
import org.junit.jupiter.api.Test;

import static com.fitsync.workout.progress.Snaps.bench;
import static com.fitsync.workout.progress.Snaps.cardio;
import static com.fitsync.workout.progress.Snaps.deadlift;
import static com.fitsync.workout.progress.Snaps.set;
import static com.fitsync.workout.progress.Snaps.sport;
import static com.fitsync.workout.progress.Snaps.squat;
import static com.fitsync.workout.progress.Snaps.strength;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Training page's rules, run on hand-built sessions without Spring. */
class TrainingProgressCalculatorTests {

    /** Monday 7 Sept to Sunday 4 Oct 2026, four weeks; the previous period is 10 Aug to 6 Sept. */
    private static final ProgressPeriod PERIOD = new ProgressPeriod(LocalDate.parse("2026-09-07"),
            LocalDate.parse("2026-10-04"));
    private static final LocalDate SATURDAY = LocalDate.parse("2026-10-03");
    private static final LocalDate AFTER = LocalDate.parse("2026-10-10");

    private static TrainingProgressView compute(List<SessionSnap> sessions, Map<UUID, BigDecimal> bestBefore,
                                                LocalDate today, BigDecimal bodyWeight, Set<DayOfWeek> planned) {
        return TrainingProgressCalculator.compute(new TrainingProgressCalculator.Inputs(PERIOD, today, sessions,
                bestBefore, bodyWeight, planned));
    }

    private static List<SessionSnap> deadliftHistory() {
        return List.of(
                strength("2026-08-20", 50, 7, deadlift(set("90", 5))),
                strength("2026-09-08", 50, 7, deadlift(set("80", 5)), squat(set("60", 5))),
                strength("2026-09-15", 50, 7, deadlift(set("90", 6)), squat(set("65", 5))),
                strength("2026-09-22", 50, 7, deadlift(set("95", 5), set("100", 2))));
    }

    @Test
    void estimatesTheBestSingleFromTheHeaviestSetOfASession() {
        assertEquals(new BigDecimal("116.6667"), StrengthMath.e1rm(new BigDecimal("100"), 5));
        ExerciseSnap snap = deadlift(set("100", 5), set("110", 1), set("90", 10));
        assertEquals(0, new BigDecimal("120").compareTo(snap.e1rm()));
        assertEquals(new BigDecimal("90"), snap.bestSet().weightKg());
        assertNull(new SetSnap(new BigDecimal("50"), null).e1rm());
        assertNull(new SetSnap(null, 10).e1rm());
    }

    @Test
    void marksRecordsAgainstAllHistoryAndSkipsAFirstTimeExercise() {
        TrainingProgressView view = compute(deadliftHistory(), Map.of(Snaps.DEADLIFT, new BigDecimal("100")),
                AFTER, null, Set.of());

        assertEquals(3, view.hero().records().value());
        assertEquals(1, view.hero().records().previous());
        assertEquals(List.of("Deadlift", "Squat"), view.hero().records().exercises());
        assertEquals(3, view.records().size());
        assertEquals(LocalDate.parse("2026-09-22"), view.records().get(0).date());
        assertEquals(new BigDecimal("110.8"), view.records().get(0).value());
        assertEquals(new BigDecimal("2.8"), view.records().get(0).delta());
        assertEquals("kg", view.records().get(0).unit());
        TrainingProgressView.RecordEntry squatRecord = view.records().stream()
                .filter(r -> r.name().equals("Squat")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("5.8"), squatRecord.delta());

        Lift lift = view.lifts().get(0);
        assertEquals(1, view.lifts().size());
        assertEquals("Deadlift", lift.name());
        assertEquals(List.of(false, true, true), lift.e1rm().stream().map(p -> p.isRecord()).toList());
        assertEquals(19, lift.changePct());
        assertEquals(new BigDecimal("95"), lift.bestSet().weightKg());
        assertEquals(5, lift.bestSet().reps());
        assertNull(lift.relative());

        assertTrue(view.calendar().stream().anyMatch(d -> d.date().equals(LocalDate.parse("2026-09-15")) && d.record()));
        assertTrue(view.calendar().stream().anyMatch(d -> d.date().equals(LocalDate.parse("2026-09-08")) && !d.record()));
    }

    @Test
    void placesTheLiftOnItsLadderFromTheBodyWeight() {
        TrainingProgressView view = compute(deadliftHistory(), Map.of(Snaps.DEADLIFT, new BigDecimal("100")),
                AFTER, new BigDecimal("80"), Set.of());

        TrainingProgressView.Relative relative = view.lifts().get(0).relative();
        assertEquals(new BigDecimal("1.39"), relative.ratio());
        assertEquals(Level.NOVICE, relative.level());
        assertEquals(Level.INTERMEDIATE, relative.nextLevel());
        assertEquals(new BigDecimal("120.0"), relative.nextAtKg());
        assertEquals(new BigDecimal("80"), view.lifts().get(0).bodyWeightKg());

        TrainingProgressView.Relative top = StrengthMath.relative("Back squat", new BigDecimal("150"), new BigDecimal("80"));
        assertEquals(Level.ADVANCED, top.level());
        assertNull(top.nextLevel());
        assertEquals(Level.BEGINNER, StrengthMath.relative("Overhead press", new BigDecimal("40"),
                new BigDecimal("80")).level());
        assertNull(StrengthMath.acceptedBodyWeight(new BigDecimal("29.9")));
        assertNull(StrengthMath.acceptedBodyWeight(new BigDecimal("300.5")));
    }

    @Test
    void countsHardSetsOnceForThePrimaryGroupAndHalfForEachSecondary() {
        List<SessionSnap> sessions = List.of(strength("2026-09-10", 60, 7,
                deadlift(set("100", 5), set("100", 5), set("100", 5), set("100", 5), new SetSnap(null, null)),
                bench(set("60", 8), set("60", 8))));

        Muscles muscles = compute(sessions, Map.of(), AFTER, null, Set.of()).muscles();

        assertEquals(new BigDecimal("4.0"), muscles.weeksCounted());
        assertEquals(new UsualRange(10, 20), muscles.usualRange());
        assertEquals(TrainingProgressCalculator.GROUPS, muscles.groups().stream().map(MuscleGroupSets::group).toList());
        assertEquals(new BigDecimal("1.0"), sets(muscles, MuscleGroup.BACK));
        assertEquals(new BigDecimal("0.5"), sets(muscles, MuscleGroup.LEGS));
        assertEquals(new BigDecimal("0.5"), sets(muscles, MuscleGroup.GLUTES));
        assertEquals(new BigDecimal("0.5"), sets(muscles, MuscleGroup.CHEST));
        assertEquals(new BigDecimal("0.3"), sets(muscles, MuscleGroup.ARMS));
        assertEquals(new BigDecimal("0.0"), sets(muscles, MuscleGroup.CORE));
        MuscleGroupSets legs = group(muscles, MuscleGroup.LEGS);
        assertEquals("Deadlift", legs.exercises().get(0).name());
        assertEquals(new BigDecimal("2.0"), legs.exercises().get(0).sets());

        Highlight under = TrainingProgressCalculator.muscleUnder(muscles);
        assertEquals("MUSCLE_UNDER", under.code());
        assertEquals("Core had 0 hard sets a week, the fewest of any group.", under.title());
        assertEquals(7, under.series().size());
    }

    @Test
    void muscleUnderStaysQuietWithoutStrengthOrWhenEveryGroupHasSixSets() {
        Muscles none = compute(List.of(cardio("2026-09-10", 30, 6, "5")), Map.of(), AFTER, null, Set.of()).muscles();
        assertNull(TrainingProgressCalculator.muscleUnder(none));

        List<MuscleGroupSets> full = TrainingProgressCalculator.GROUPS.stream()
                .map(g -> new MuscleGroupSets(g, new BigDecimal("8.0"), List.of())).toList();
        assertNull(TrainingProgressCalculator.muscleUnder(new Muscles(new BigDecimal("4.0"), full,
                new UsualRange(10, 20))));
    }

    @Test
    void loadUsesTheKindDefaultEffortAndMarksItEstimated() {
        TrainingProgressView view = compute(List.of(
                strength("2026-09-08", 60, null, deadlift(set("100", 5))),
                strength("2026-09-09", 30, 8, deadlift(set("100", 5)))), Map.of(), AFTER, null, Set.of());

        assertEquals(600, view.hero().load().value());
        assertEquals(new BigDecimal("0.60"), view.hero().load().estimatedShare());
        TrainingProgressView.LoadWeek week = view.load().weeks().stream()
                .filter(w -> w.weekStart().equals(LocalDate.parse("2026-09-07"))).findFirst().orElseThrow();
        assertEquals(600, week.total());
        assertEquals(600, week.byKind().strength());
        assertTrue(week.estimated());
        assertEquals(8, view.load().weeks().size());
        assertEquals(LocalDate.parse("2026-08-10"), view.load().weeks().get(0).weekStart());
        assertEquals(600, view.load().fatigue().get(4).value());
        assertEquals(100, view.load().fitness().get(4).value());
    }

    @Test
    void fitnessIsTheSixWeekMeanAndFormIsOnlyAWord() {
        LocalDate week = LocalDate.parse("2026-09-28");
        Map<LocalDate, Long> totals = new HashMap<>();
        long[] loads = {600, 600, 600, 600, 600, 1200};
        for (int i = 0; i < loads.length; i++) {
            totals.put(week.minusWeeks(5L - i), loads[i]);
        }
        assertEquals(700, TrainingProgressCalculator.fitnessAt(totals, week));
        assertEquals(Form.TIRED, TrainingProgressCalculator.form(1000, 1300));
        assertEquals(Form.FRESH, TrainingProgressCalculator.form(1000, 600));
        assertEquals(Form.BALANCED, TrainingProgressCalculator.form(1000, 1100));
        assertEquals(Form.BALANCED, TrainingProgressCalculator.form(0, 0));
    }

    @Test
    void loadTrendDescribesTheRunOfWeeksAndTheWeekInProgress() {
        List<SessionSnap> sessions = List.of(
                strength("2026-08-11", 50, null), strength("2026-08-18", 50, null),
                strength("2026-08-25", 50, null), strength("2026-09-01", 50, null),
                strength("2026-09-08", 50, null), strength("2026-09-15", 60, null),
                strength("2026-09-22", 70, null), strength("2026-09-29", 30, null));

        TrainingProgressView view = compute(sessions, Map.of(), SATURDAY, null, Set.of());
        Highlight load = view.highlights().stream().filter(h -> h.code().equals("LOAD_TREND")).findFirst().orElseThrow();

        assertEquals("Load rose for two weeks, and this week is lighter so far.", load.title());
        assertEquals("+5% on the 4 weeks before, effort × minutes each week", load.context());
        // The series ends at the last finished week; the week in progress is the labelled "so far" bar.
        assertEquals(3, load.series().size());
        assertEquals("2026-09-07", load.series().get(0).label());
        assertEquals(LocalDate.parse("2026-09-28"), view.load().currentWeek().weekStart());
        assertEquals(180, view.load().currentWeek().soFar());
        assertEquals(1260, view.hero().load().value());
        assertEquals(1200, view.hero().load().previous());
        // Form reads the last finished week (420 against a six-week mean of 330), not the week in progress.
        assertEquals(Form.TIRED, view.load().form());
    }

    @Test
    void todayIsNeverAWeekAndNoComparisonCountsIt() {
        // Four full weeks to Sunday 4 Oct, a strength session every Tuesday, then a short one on Monday 5 Oct.
        LocalDate monday = LocalDate.parse("2026-10-05");
        ProgressPeriod period = new ProgressPeriod(LocalDate.parse("2026-09-08"), monday);
        List<SessionSnap> sessions = new java.util.ArrayList<>();
        for (LocalDate tuesday = LocalDate.parse("2026-08-11"); tuesday.isBefore(monday); tuesday = tuesday.plusWeeks(1)) {
            sessions.add(strength(tuesday.toString(), 60, 6));
        }
        sessions.add(strength(monday.toString(), 20, 6));

        TrainingProgressView view = TrainingProgressCalculator.compute(new TrainingProgressCalculator.Inputs(period,
                monday, sessions, Map.of(), null, Set.of()));

        assertEquals(4, view.hero().sessions().value());
        assertEquals(4, view.hero().sessions().previous());
        assertEquals(view.hero().load().previous(), view.hero().load().value());
        assertEquals(LocalDate.parse("2026-09-28"), view.load().fatigue().get(view.load().fatigue().size() - 1).weekStart());
        assertEquals(LocalDate.parse("2026-09-28"), view.load().fitness().get(view.load().fitness().size() - 1).weekStart());
        assertEquals(monday, view.load().weeks().get(view.load().weeks().size() - 1).weekStart());
        assertEquals(monday, view.load().currentWeek().weekStart());
        assertEquals(120, view.load().currentWeek().soFar());
        assertEquals(Form.BALANCED, view.load().form());

        Highlight load = view.highlights().stream().filter(h -> h.code().equals("LOAD_TREND")).findFirst().orElseThrow();
        assertEquals("Load held about level.", load.title());
        assertEquals("2026-09-28", load.series().get(load.series().size() - 1).label());

        Activity strength = view.activities().get(0);
        assertEquals(5, strength.sessions());
        assertTrue(strength.summary().stream().noneMatch(line -> line.trend() == Trend.DOWN));
        assertTrue(strength.context().startsWith("Same time as"), strength.context());
    }

    @Test
    void loadTrendNeedsTheFourWeeksBefore() {
        TrainingProgressView view = compute(List.of(strength("2026-09-08", 50, 6)), Map.of(), SATURDAY, null, Set.of());
        assertTrue(view.highlights().stream().noneMatch(h -> h.code().equals("LOAD_TREND")));
    }

    @Test
    void liftUpFiresAboveFivePercentAndNotBelow() {
        TrainingProgressView up = compute(deadliftHistory(), Map.of(), AFTER, null, Set.of());
        Highlight lift = up.highlights().get(0);
        assertEquals("LIFT_UP", lift.code());
        assertEquals("Your deadlift's estimated best single is up 19% in four weeks.", lift.title());
        assertEquals("+17.5 kg compared with 8 Sept, 2 records in these four weeks", lift.context());
        assertEquals("kg", lift.unit());
        assertEquals(3, lift.series().size());

        TrainingProgressView flat = compute(List.of(
                strength("2026-09-08", 50, 7, deadlift(set("100", 5))),
                strength("2026-09-15", 50, 7, deadlift(set("100", 5))),
                strength("2026-09-22", 50, 7, deadlift(set("103", 5)))), Map.of(), AFTER, null, Set.of());
        assertTrue(flat.highlights().stream().noneMatch(h -> h.code().equals("LIFT_UP")));
        assertEquals(3, flat.lifts().get(0).changePct());
    }

    @Test
    void paceFasterComparesTheFirstAndLastRunOfThePeriod() {
        TrainingProgressView view = compute(List.of(cardio("2026-09-08", 30, 6, "5"), cardio("2026-09-20", 30, 6, "5.2")),
                Map.of(), AFTER, null, Set.of());
        Highlight pace = view.highlights().stream().filter(h -> h.code().equals("PACE_FASTER")).findFirst().orElseThrow();
        assertEquals("You run a kilometre 14 seconds faster.", pace.title());
        assertEquals("Treadmill, 14 seconds a km faster than on 8 Sept", pace.context());
        assertEquals(new BigDecimal("346"), pace.series().get(1).value());

        TrainingProgressView single = compute(List.of(cardio("2026-09-08", 30, 6, "5"), cardio("2026-09-20", 30, 6, null)),
                Map.of(), AFTER, null, Set.of());
        assertTrue(single.highlights().stream().noneMatch(h -> h.code().equals("PACE_FASTER")));
    }

    @Test
    void consistencyCountsPlannedWeekdaysOnlyWithAnActivePlan() {
        List<SessionSnap> sessions = List.of(strength("2026-09-07", 40, 6), strength("2026-09-10", 40, 6),
                strength("2026-09-12", 40, 6));
        TrainingProgressView planned = compute(sessions, Map.of(), AFTER, null,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY));
        Highlight consistency = planned.highlights().stream().filter(h -> h.code().equals("CONSISTENCY"))
                .findFirst().orElseThrow();
        assertEquals("You trained on 2 of 8 planned days, and on 1 day besides.", consistency.title());
        assertEquals("2 planned days a week in your active plan", consistency.context());
        assertEquals(4, consistency.series().size());
        assertEquals(new BigDecimal("3"), consistency.series().get(0).value());

        TrainingProgressView unplanned = compute(sessions, Map.of(), AFTER, null, Set.of());
        assertTrue(unplanned.highlights().stream().noneMatch(h -> h.code().equals("CONSISTENCY")));
    }

    @Test
    void keepsAtMostFourHighlightsInTheOrderOfTheRules() {
        List<SessionSnap> sessions = new java.util.ArrayList<>(deadliftHistory());
        sessions.add(strength("2026-08-25", 50, 7));
        sessions.add(cardio("2026-09-09", 30, 6, "5"));
        sessions.add(cardio("2026-09-21", 30, 6, "5.2"));
        sessions.sort(java.util.Comparator.comparing(SessionSnap::date));

        TrainingProgressView view = compute(sessions, Map.of(), AFTER, null, Set.of(DayOfWeek.TUESDAY));

        assertEquals(List.of("LIFT_UP", "PACE_FASTER", "MUSCLE_UNDER", "LOAD_TREND"),
                view.highlights().stream().map(Highlight::code).toList());
    }

    @Test
    void summarisesASportInThreeLinesWithTheTimeAgainstThePeriodBefore() {
        List<SessionSnap> sessions = List.of(
                sport("2026-08-16", "15:00", SportActivity.BADMINTON, 60, 6),
                sport("2026-08-23", "15:00", SportActivity.BADMINTON, 45, 5),
                sport("2026-09-13", "15:00", SportActivity.BADMINTON, 60, 5),
                sport("2026-09-20", "15:10", SportActivity.BADMINTON, 60, 6),
                sport("2026-09-27", "15:20", SportActivity.BADMINTON, 65, 7));

        Activity badminton = compute(sessions, Map.of(), SATURDAY, null, Set.of()).activities().get(0);

        assertEquals("SPORT:BADMINTON", badminton.key());
        assertEquals("Badminton", badminton.name());
        assertEquals(3, badminton.sessions());
        assertEquals(185, badminton.minutes());
        assertEquals(List.of(60, 120, 185), badminton.cumulativeMinutes().stream().map(p -> p.minutes()).toList());
        assertEquals(List.of(
                new SummaryLine("Once a week,", "Once a week, 3 weeks in a row", Trend.UP),
                new SummaryLine("60 → 65 min", "60 → 65 min a session", Trend.UP),
                new SummaryLine("Effort 5 → 7", "Effort 5 → 7 of 10", Trend.UP)), badminton.summary());
        assertEquals("+1 h 20 min on the 4 weeks before", badminton.context());
    }

    @Test
    void summarisesCardioByPaceDistanceAndStrengthByTheLifts() {
        List<SessionSnap> sessions = new java.util.ArrayList<>(deadliftHistory());
        sessions.add(cardio("2026-08-20", 30, 6, "3.2"));
        sessions.add(cardio("2026-09-09", 30, 6, "5"));
        sessions.add(cardio("2026-09-21", 30, 6, "5.2"));
        sessions.sort(java.util.Comparator.comparing(SessionSnap::date));

        List<Activity> activities = compute(sessions, Map.of(), AFTER, null, Set.of()).activities();

        assertEquals(List.of("STRENGTH", "CARDIO"), activities.stream().map(Activity::key).toList());
        Activity cardio = activities.get(1);
        assertEquals("Treadmill", cardio.name());
        assertEquals(List.of("Pace 6:00 → 5:46 a km", "Longest run 3.2 → 5.2 km", "10.2 km in all"),
                cardio.summary().stream().map(SummaryLine::text).toList());
        assertEquals(List.of(Trend.UP, Trend.UP, Trend.UP), cardio.summary().stream().map(SummaryLine::trend).toList());
        assertEquals("+30 min on the 4 weeks before", cardio.context());

        Activity strength = activities.get(0);
        assertEquals("e1RM +19% across one lift", strength.summary().get(0).text());
        assertEquals("e1RM +19%", strength.summary().get(0).figure());
        assertEquals("2 hard sets a week", strength.summary().get(1).text());
        assertEquals("0 or 1 sessions a week", strength.summary().get(2).text());
        assertEquals("+1 h 40 min on the 4 weeks before", strength.context());
    }

    @Test
    void periodWindowsAcrossAMondayAndTheirLimits() {
        ProgressPeriod week = new ProgressPeriod(LocalDate.parse("2026-09-30"), LocalDate.parse("2026-10-06"));
        assertEquals(new ProgressPeriod(LocalDate.parse("2026-09-23"), LocalDate.parse("2026-09-29")), week.previous());
        assertEquals(LocalDate.parse("2026-09-28"), ProgressPeriod.mondayOf(LocalDate.parse("2026-10-04")));
        assertEquals(LocalDate.parse("2026-08-10"), PERIOD.previous().from());
        assertEquals(LocalDate.parse("2026-09-06"), PERIOD.previous().to());
        assertEquals(370, new ProgressPeriod(LocalDate.parse("2025-10-01"), LocalDate.parse("2026-10-05")).days());
        assertThrowsBadRequest(() -> new ProgressPeriod(LocalDate.parse("2026-10-02"), LocalDate.parse("2026-10-01")));
        assertThrowsBadRequest(() -> new ProgressPeriod(LocalDate.parse("2025-10-01"), LocalDate.parse("2026-10-06")));
        assertThrowsBadRequest(() -> new ProgressPeriod(null, LocalDate.parse("2026-10-06")));
        assertFalse(PERIOD.contains(LocalDate.parse("2026-09-06")));
    }

    @Test
    void writesNumbersTheWayTheBoardsDo() {
        assertEquals("8 Sept", ProgressText.date(LocalDate.parse("2026-09-08")));
        assertEquals("12 July", ProgressText.date(LocalDate.parse("2026-07-12")));
        assertEquals("1 h 10 min", ProgressText.hm(70));
        assertEquals("−10 min", ProgressText.signedHm(-10));
        assertEquals("93.3", ProgressText.kg(new BigDecimal("93.33")));
        assertEquals("80", ProgressText.kg(new BigDecimal("80.00")));
        assertEquals("5:50", ProgressText.pace(350));
        assertEquals("1.9 t", ProgressText.volume(new BigDecimal("1912")));
        assertEquals("the 3 months before", ProgressText.previousLabel(91));
        assertEquals("a year", ProgressText.periodPhrase(365));
        assertEquals(12, StrengthMath.pctChange(new BigDecimal("100"), new BigDecimal("112.5")));
    }

    private static void assertThrowsBadRequest(org.junit.jupiter.api.function.Executable executable) {
        org.junit.jupiter.api.Assertions.assertThrows(com.fitsync.web.error.BadRequestException.class, executable);
    }

    private static BigDecimal sets(Muscles muscles, MuscleGroup group) {
        return group(muscles, group).setsPerWeek();
    }

    private static MuscleGroupSets group(Muscles muscles, MuscleGroup group) {
        return muscles.groups().stream().filter(g -> g.group() == group).findFirst().orElseThrow();
    }
}
