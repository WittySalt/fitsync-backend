package com.fitsync.workout.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.SegmentKind;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.dto.PlanEntryInput;
import com.fitsync.workout.dto.WorkoutPlanDayInput;
import com.fitsync.workout.dto.WorkoutPlanExerciseInput;
import org.junit.jupiter.api.Test;

/** The rules of a plan day's entries, and the shapes the server lays out for segments. */
class PlanEntryRulesTests {

    private static final UUID SQUAT = UUID.randomUUID();

    @Test
    void aDayOfMovesOrOfSegmentsPasses() {
        assertDoesNotThrow(() -> PlanEntryRules.validateDay(day(SessionKind.STRENGTH, 45,
                List.of(PlanEntryInput.set(SQUAT, 1, 3, 10, null, 90)))));
        assertDoesNotThrow(() -> PlanEntryRules.validateDay(day(SessionKind.CARDIO, 30, steadyThirty())));
    }

    @Test
    void aDayNeverMixesMovesAndSegments() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PlanEntryRules.validateDay(day(SessionKind.CARDIO, 30, List.of(
                        PlanEntryInput.set(SQUAT, 1, 3, 10, null, 90),
                        PlanEntryInput.segment(SegmentKind.STEADY, "Steady", 2, 1800, null)))));
        assertEquals(PlanEntryRules.MIXED_DAY_MESSAGE, error.getMessage());
    }

    @Test
    void strengthAndMobilityDaysHoldMovesAndCardioDaysHoldSegments() {
        assertEquals(PlanEntryRules.SEGMENTS_ON_MOVES_DAY_MESSAGE, assertThrows(IllegalArgumentException.class,
                () -> PlanEntryRules.validateDay(day(SessionKind.STRENGTH, 30, steadyThirty()))).getMessage());
        assertEquals(PlanEntryRules.SEGMENTS_ON_MOVES_DAY_MESSAGE, assertThrows(IllegalArgumentException.class,
                () -> PlanEntryRules.validateDay(day(SessionKind.MOBILITY, 30, steadyThirty()))).getMessage());
        assertEquals(PlanEntryRules.MOVES_ON_SEGMENTS_DAY_MESSAGE, assertThrows(IllegalArgumentException.class,
                () -> PlanEntryRules.validateDay(day(SessionKind.CARDIO, 30,
                        List.of(PlanEntryInput.set(SQUAT, 1, 3, 10, null, 90))))).getMessage());
        assertEquals(PlanEntryRules.SPORT_DAY_HAS_NO_MOVES_MESSAGE, assertThrows(IllegalArgumentException.class,
                () -> PlanEntryRules.validateDay(day(SessionKind.SPORT, 30,
                        List.of(PlanEntryInput.set(SQUAT, 1, 3, 10, null, 90))))).getMessage());
    }

    @Test
    void segmentsAddUpToTheDayWithinAMinute() {
        assertDoesNotThrow(() -> PlanEntryRules.validateDay(day(SessionKind.CARDIO, 31, steadyThirty())));
        assertDoesNotThrow(() -> PlanEntryRules.validateDay(day(SessionKind.CARDIO, 29, steadyThirty())));
        assertEquals(PlanEntryRules.SEGMENTS_MUST_FILL_DAY_MESSAGE, assertThrows(IllegalArgumentException.class,
                () -> PlanEntryRules.validateDay(day(SessionKind.CARDIO, 32, steadyThirty()))).getMessage());
    }

    @Test
    void roundsCountEachTime() {
        List<PlanEntryInput> intervals = List.of(
                PlanEntryInput.segment(SegmentKind.WARMUP, null, 1, 300, null),
                PlanEntryInput.segment(SegmentKind.WORK, null, 2, 30, 8),
                PlanEntryInput.segment(SegmentKind.EASY, null, 3, 60, 8),
                PlanEntryInput.segment(SegmentKind.COOLDOWN, null, 4, 300, null));
        assertEquals(300 + 8 * 90 + 300, PlanEntryRules.totalSeconds(intervals));
        assertDoesNotThrow(() -> PlanEntryRules.validateDay(day(SessionKind.CARDIO, 22, intervals)));
    }

    @Test
    void movesNeedAnExerciseAndSegmentsAKindAndALength() {
        assertEquals(PlanEntryRules.MOVE_NEEDS_EXERCISE_MESSAGE, assertThrows(IllegalArgumentException.class,
                () -> PlanEntryRules.validateDay(day(SessionKind.STRENGTH, 30,
                        List.of(PlanEntryInput.set(null, 1, 3, 10, null, 90))))).getMessage());
        assertEquals(PlanEntryRules.SEGMENT_NEEDS_KIND_MESSAGE, assertThrows(IllegalArgumentException.class,
                () -> PlanEntryRules.validateDay(day(SessionKind.CARDIO, null,
                        List.of(PlanEntryInput.segment(null, "Steady", 1, 600, null))))).getMessage());
    }

    @Test
    void positionsAreUniqueAndEntriesAndExercisesAreNotBothSent() {
        assertEquals(PlanEntryRules.DUPLICATE_POSITION_MESSAGE, assertThrows(IllegalArgumentException.class,
                () -> PlanEntryRules.validateDay(day(SessionKind.STRENGTH, 30, List.of(
                        PlanEntryInput.set(SQUAT, 1, 3, 10, null, 90),
                        PlanEntryInput.set(SQUAT, 1, 3, 10, null, 90))))).getMessage());
        WorkoutPlanDayInput both = new WorkoutPlanDayInput(null, 1, SessionKind.STRENGTH, "Legs", null, 30,
                List.of(new WorkoutPlanExerciseInput(SQUAT, 1, 3, 10, null, null, null, 90, null, List.of())),
                null, null, null, null, List.of(PlanEntryInput.set(SQUAT, 1, 3, 10, null, 90)));
        assertEquals(PlanEntryRules.ENTRIES_OR_EXERCISES_MESSAGE, assertThrows(IllegalArgumentException.class,
                () -> PlanEntryRules.validateDay(both)).getMessage());
    }

    @Test
    void theDefaultShapeIsFiveTheRestFive() {
        List<PlanEntryInput> segments = PlanEntryRules.defaultSegments(45, null);
        assertEquals(List.of(SegmentKind.WARMUP, SegmentKind.STEADY, SegmentKind.COOLDOWN),
                segments.stream().map(PlanEntryInput::kind).toList());
        assertEquals(List.of(300, 2100, 300), segments.stream().map(PlanEntryInput::plannedSeconds).toList());
        assertEquals("Badminton", PlanEntryRules.defaultSegments(60, SportActivity.BADMINTON).get(1).label());
        assertEquals(1, PlanEntryRules.defaultSegments(8, null).size());
        assertEquals(480, PlanEntryRules.defaultSegments(8, null).get(0).plannedSeconds());
        assertTrue(PlanEntryRules.defaultSegments(null, null).isEmpty());
    }

    @Test
    void roundsExpandTogetherInOrder() {
        List<PlanEntryRules.SegmentSpec> expanded = PlanEntryRules.expand(PlanEntryRules.defaultIntervals());
        assertEquals(18, expanded.size());
        assertEquals("Warm-up", expanded.get(0).label());
        assertEquals("Work 1", expanded.get(1).label());
        assertEquals("Easy 1", expanded.get(2).label());
        assertEquals("Work 8", expanded.get(15).label());
        assertEquals("Easy 8", expanded.get(16).label());
        assertEquals("Cool-down", expanded.get(17).label());
        assertEquals(30, expanded.get(1).plannedSeconds());
        assertEquals(60, expanded.get(2).plannedSeconds());
    }

    private static List<PlanEntryInput> steadyThirty() {
        return List.of(
                PlanEntryInput.segment(SegmentKind.WARMUP, "Warm-up", 1, 300, null),
                PlanEntryInput.segment(SegmentKind.STEADY, "Steady", 2, 1200, null),
                PlanEntryInput.segment(SegmentKind.COOLDOWN, "Cool-down", 3, 300, null));
    }

    private static WorkoutPlanDayInput day(SessionKind kind, Integer minutes, List<PlanEntryInput> entries) {
        return new WorkoutPlanDayInput(null, 1, kind, "Day", kind == SessionKind.SPORT ? SportActivity.BADMINTON : null,
                minutes, List.of(), null, null, null, null, entries);
    }
}
