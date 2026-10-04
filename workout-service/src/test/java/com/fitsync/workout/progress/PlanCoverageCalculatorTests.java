package com.fitsync.workout.progress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.dto.PlanCoverageView;
import com.fitsync.workout.progress.PlanCoverageCalculator.Day;
import com.fitsync.workout.progress.PlanCoverageCalculator.Move;
import org.junit.jupiter.api.Test;

/** What a plan trains: the Progress hard-set rule over the planned sets, and the editor's five notes. */
class PlanCoverageCalculatorTests {

    private static final Move BENCH = new Move("Dumbbell Bench Press", MuscleGroup.CHEST,
            Set.of(MuscleGroup.SHOULDERS, MuscleGroup.ARMS), 3);
    private static final Move SQUAT = new Move("Goblet Squat", MuscleGroup.LEGS, Set.of(MuscleGroup.GLUTES), 3);
    private static final Move CALF = new Move("Standing Calf Raises", MuscleGroup.LEGS, Set.of(), 3);
    private static final Move PULLDOWN = new Move("Wide-Grip Lat Pulldown", MuscleGroup.BACK, Set.of(MuscleGroup.ARMS), 3);
    private static final Move PRESS = new Move("Dumbbell Shoulder Press", MuscleGroup.SHOULDERS, Set.of(), 3);

    @Test
    void primaryCountsOneAndSecondaryHalfPerSet() {
        PlanCoverageView coverage = PlanCoverageCalculator.calculate(List.of(
                new Day(1, SessionKind.STRENGTH, 45, List.of(BENCH, SQUAT)),
                new Day(4, SessionKind.STRENGTH, 45, List.of(BENCH))), null);

        assertEquals(new BigDecimal("6.0"), sets(coverage, MuscleGroup.CHEST));
        assertEquals(new BigDecimal("3.0"), sets(coverage, MuscleGroup.SHOULDERS));
        assertEquals(new BigDecimal("3.0"), sets(coverage, MuscleGroup.ARMS));
        assertEquals(new BigDecimal("3.0"), sets(coverage, MuscleGroup.LEGS));
        assertEquals(new BigDecimal("1.5"), sets(coverage, MuscleGroup.GLUTES));
        assertEquals(new BigDecimal("0.0"), sets(coverage, MuscleGroup.BACK));
        assertEquals(7, coverage.groups().size());
    }

    @Test
    void onlyStrengthDaysCount() {
        PlanCoverageView coverage = PlanCoverageCalculator.calculate(List.of(
                new Day(1, SessionKind.MOBILITY, 30, List.of(BENCH))), null);
        assertEquals(new BigDecimal("0.0"), sets(coverage, MuscleGroup.CHEST));
        assertTrue(coverage.notes().isEmpty());
    }

    @Test
    void calvesPullFromAboveAndShoulderNotes() {
        PlanCoverageView coverage = PlanCoverageCalculator.calculate(List.of(
                new Day(1, SessionKind.STRENGTH, 45, List.of(BENCH, SQUAT))), null);
        assertEquals(List.of(PlanCoverageCalculator.CALVES_NOT_TRAINED, PlanCoverageCalculator.NO_VERTICAL_PULL,
                PlanCoverageCalculator.SHOULDERS_HELPER_ONLY), codes(coverage));
        assertEquals("Calves are not trained yet.", coverage.notes().get(0).text());
        assertEquals("No pull from above yet (lats).", coverage.notes().get(1).text());
        assertEquals("Shoulders only as a helper.", coverage.notes().get(2).text());

        PlanCoverageView covered = PlanCoverageCalculator.calculate(List.of(
                new Day(1, SessionKind.STRENGTH, 45, List.of(BENCH, SQUAT, CALF, PULLDOWN, PRESS))), null);
        assertTrue(covered.notes().isEmpty());
    }

    @Test
    void strengthDaysBackToBackIncludingSundayToMonday() {
        assertEquals(List.of(PlanCoverageCalculator.STRENGTH_BACK_TO_BACK), codes(PlanCoverageCalculator.calculate(
                List.of(new Day(1, SessionKind.STRENGTH, 45, List.of()), new Day(2, SessionKind.STRENGTH, 45, List.of())),
                null)));
        assertEquals(List.of(PlanCoverageCalculator.STRENGTH_BACK_TO_BACK), codes(PlanCoverageCalculator.calculate(
                List.of(new Day(7, SessionKind.STRENGTH, 45, List.of()), new Day(1, SessionKind.STRENGTH, 45, List.of())),
                null)));
        assertTrue(codes(PlanCoverageCalculator.calculate(
                List.of(new Day(1, SessionKind.STRENGTH, 45, List.of()), new Day(3, SessionKind.STRENGTH, 45, List.of()),
                        new Day(2, SessionKind.CARDIO, 30, List.of())), null)).isEmpty());
    }

    @Test
    void overTheProfileByHalfOrMore() {
        List<Day> week = List.of(new Day(1, SessionKind.CARDIO, 150, List.of()),
                new Day(3, SessionKind.CARDIO, 150, List.of()));
        assertEquals(List.of(PlanCoverageCalculator.OVER_PROFILE_MINUTES),
                codes(PlanCoverageCalculator.calculate(week, 200)));
        assertEquals("That is more than your profile says you have.",
                PlanCoverageCalculator.calculate(week, 200).notes().get(0).text());
        assertTrue(codes(PlanCoverageCalculator.calculate(week, 201)).isEmpty());
        assertTrue(codes(PlanCoverageCalculator.calculate(week, null)).isEmpty());
    }

    private static BigDecimal sets(PlanCoverageView coverage, MuscleGroup group) {
        return coverage.groups().stream().filter(g -> g.group() == group).findFirst().orElseThrow().setsPerWeek();
    }

    private static List<String> codes(PlanCoverageView coverage) {
        return coverage.notes().stream().map(PlanCoverageView.Note::code).toList();
    }
}
