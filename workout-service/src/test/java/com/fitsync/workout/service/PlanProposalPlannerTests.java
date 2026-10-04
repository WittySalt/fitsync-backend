package com.fitsync.workout.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fitsync.workout.domain.DayTargetKind;
import com.fitsync.workout.domain.EntryType;
import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.dto.PlanEntryInput;
import com.fitsync.workout.dto.WorkoutPlanDayInput;
import org.junit.jupiter.api.Test;

/** The default week (spec 2.2) and the habit days it starts from. */
class PlanProposalPlannerTests {

    /** Offers a fresh move for every slot, named after its first curated move, with dumbbells. */
    private static final PlanProposalPlanner.MovePicker PICKER = new PlanProposalPlanner.MovePicker() {
        @Override
        public Optional<PlanProposalPlanner.Move> pick(PlanProposalPlanner.Slot slot, boolean beginner,
                                                       Set<UUID> used) {
            return Optional.of(new PlanProposalPlanner.Move(UUID.randomUUID(), slot.curated().get(0),
                    ExerciseEquipment.DUMBBELL));
        }

        @Override
        public List<PlanProposalPlanner.Move> mobility(int count, boolean beginner) {
            List<PlanProposalPlanner.Move> moves = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                moves.add(new PlanProposalPlanner.Move(UUID.randomUUID(), "Stretch " + index, ExerciseEquipment.NONE));
            }
            return moves;
        }
    };

    private static final List<PlanProposalPlanner.Candidate> CATALOGUE = List.of(
            candidate("Machine Bench Press", ExerciseEquipment.MACHINE),
            candidate("Barbell Bench Press - Medium Grip", ExerciseEquipment.BARBELL),
            candidate("Dumbbell Bench Press", ExerciseEquipment.DUMBBELL),
            candidate("Clock Push-Up", ExerciseEquipment.BODYWEIGHT),
            candidate("Pushups", ExerciseEquipment.BODYWEIGHT),
            candidate("V-Bar Pulldown", ExerciseEquipment.CABLE),
            candidate("Wide-Grip Lat Pulldown", ExerciseEquipment.CABLE),
            candidate("Pullups", ExerciseEquipment.BODYWEIGHT),
            candidate("Drag Curl", ExerciseEquipment.BARBELL));

    @Test
    void theCuratedListPicksByLevelAndEquipment() {
        Set<UUID> none = Set.of();
        assertEquals("Barbell Bench Press - Medium Grip",
                PlanProposalPlanner.chooseCurated(CATALOGUE, PlanProposalPlanner.PUSH.curated(), false, none).name());
        assertEquals("Dumbbell Bench Press",
                PlanProposalPlanner.chooseCurated(CATALOGUE, PlanProposalPlanner.PUSH.curated(), true, none).name());
        assertEquals("Pullups",
                PlanProposalPlanner.chooseCurated(CATALOGUE, PlanProposalPlanner.VERTICAL_PULL.curated(), true, none).name());
        assertEquals("Wide-Grip Lat Pulldown",
                PlanProposalPlanner.chooseCurated(CATALOGUE, PlanProposalPlanner.VERTICAL_PULL.curated(), false, none)
                        .name());
        Set<UUID> benchUsed = Set.of(CATALOGUE.get(2).exerciseId());
        assertEquals("Pushups",
                PlanProposalPlanner.chooseCurated(CATALOGUE, PlanProposalPlanner.PUSH.curated(), true, benchUsed).name());
        assertNull(PlanProposalPlanner.chooseCurated(CATALOGUE, PlanProposalPlanner.HINGE.curated(), false, none));
    }

    @Test
    void proposedTargetsFollowTheLevelAndTheMove() {
        PlanEntryInput beginner = PlanProposalPlanner.strengthEntry(PlanProposalPlanner.PUSH,
                move("Dumbbell Bench Press", ExerciseEquipment.DUMBBELL), true, 1);
        assertEquals(3, beginner.targetSets());
        assertEquals(10, beginner.targetReps());
        assertEquals(90, beginner.restSeconds());
        assertNull(beginner.targetWeightKg());
        assertNull(beginner.tempoSeconds());

        PlanEntryInput barbell = PlanProposalPlanner.strengthEntry(PlanProposalPlanner.PUSH,
                move("Barbell Bench Press", ExerciseEquipment.BARBELL), false, 1);
        assertEquals(8, barbell.targetReps());
        assertEquals(90, barbell.restSeconds());

        PlanEntryInput isolation = PlanProposalPlanner.strengthEntry(PlanProposalPlanner.LATERAL,
                move("Side Lateral Raise", ExerciseEquipment.DUMBBELL), false, 1);
        assertEquals(12, isolation.targetReps());
        assertEquals(60, isolation.restSeconds());

        PlanEntryInput plank = PlanProposalPlanner.strengthEntry(PlanProposalPlanner.CORE,
                move("Plank", ExerciseEquipment.BODYWEIGHT), true, 1);
        assertNull(plank.targetReps());
        assertEquals(45, plank.targetDurationSeconds());
    }

    private static PlanProposalPlanner.Candidate candidate(String name, ExerciseEquipment equipment) {
        return new PlanProposalPlanner.Candidate(UUID.randomUUID(), name, equipment);
    }

    private static PlanProposalPlanner.Move move(String name, ExerciseEquipment equipment) {
        return new PlanProposalPlanner.Move(UUID.randomUUID(), name, equipment);
    }

    @Test
    void aBeginnerGetsFullBodyDaysWithARestDayBetween() {
        List<WorkoutPlanDayInput> week = PlanProposalPlanner.propose(profile(WorkoutDifficulty.BEGINNER, 3, 45),
                List.of(), PICKER);

        assertEquals(List.of(1, 4, 6), week.stream().map(WorkoutPlanDayInput::dayOfWeek).toList());
        assertEquals(List.of("Full body A", "Full body B", "Full body C"),
                week.stream().map(WorkoutPlanDayInput::title).toList());
        assertTrue(week.stream().allMatch(day -> day.kind() == SessionKind.STRENGTH));
        assertEquals(5, week.get(0).entries().size());
        assertTrue(week.get(0).entries().stream().allMatch(entry -> entry.type() == EntryType.SET));
        assertEquals("Full body, 3 days", PlanProposalPlanner.nameOf(week));
    }

    @Test
    void someExperienceGetsUpperLowerFull() {
        List<WorkoutPlanDayInput> week = PlanProposalPlanner.propose(profile(WorkoutDifficulty.INTERMEDIATE, 3, 60),
                List.of(), PICKER);
        assertEquals(List.of("Upper", "Lower", "Full body"), week.stream().map(WorkoutPlanDayInput::title).toList());
        assertEquals(7, week.get(0).entries().size());
    }

    @Test
    void habitDaysComeFirstThenStrengthThenCardioThenMobility() {
        HabitDays.Habit badminton = new HabitDays.Habit(DayOfWeek.SATURDAY, SportActivity.BADMINTON, 60, 6);
        PlanProposalPlanner.Profile profile = new PlanProposalPlanner.Profile(WorkoutDifficulty.BEGINNER, 6, 45,
                Set.of("FULL_BODY", "CARDIO"));

        List<WorkoutPlanDayInput> week = PlanProposalPlanner.propose(profile, List.of(badminton), PICKER);

        assertEquals(6, week.size());
        WorkoutPlanDayInput saturday = week.stream().filter(day -> day.dayOfWeek() == 6).findFirst().orElseThrow();
        assertEquals(SessionKind.SPORT, saturday.kind());
        assertEquals(SportActivity.BADMINTON, saturday.sportActivity());
        assertEquals(3, saturday.entries().size());
        assertEquals(List.of(1, 4), week.stream().filter(day -> day.kind() == SessionKind.STRENGTH)
                .map(WorkoutPlanDayInput::dayOfWeek).toList());
        WorkoutPlanDayInput run = week.stream().filter(day -> day.kind() == SessionKind.CARDIO).findFirst().orElseThrow();
        assertEquals(2, run.dayOfWeek());
        assertEquals("Easy run", run.title());
        assertEquals(30, run.estimatedDurationMinutes());
        assertEquals(DayTargetKind.MINUTES, run.targetKind());
        assertEquals(3, run.effortTarget());
        assertEquals(1800, PlanEntryRules.totalSeconds(run.entries()));
        List<WorkoutPlanDayInput> mobility = week.stream().filter(day -> day.kind() == SessionKind.MOBILITY).toList();
        assertEquals(List.of(3, 7), mobility.stream().map(WorkoutPlanDayInput::dayOfWeek).sorted().toList());
        assertEquals(6, mobility.get(0).entries().size());
        assertEquals("Strength, cardio and badminton, 6 days", PlanProposalPlanner.nameOf(week));
    }

    @Test
    void daysAreKeptBetweenTwoAndSix() {
        assertEquals(2, PlanProposalPlanner.propose(profile(WorkoutDifficulty.BEGINNER, 1, 45), List.of(), PICKER).size());
        assertEquals(6, PlanProposalPlanner.propose(profile(WorkoutDifficulty.BEGINNER, 7, 45), List.of(), PICKER).size());
    }

    @Test
    void aSportPlayedOnTheSameWeekdayThreeWeeksInARowIsAHabit() {
        LocalDate today = LocalDate.parse("2026-10-04");
        List<HabitDays.SessionDay> sessions = List.of(
                sport("2026-10-03", 60), sport("2026-09-26", 70), sport("2026-09-19", 60),
                new HabitDays.SessionDay(LocalDate.parse("2026-10-01"), SessionKind.STRENGTH, null, 45));

        List<HabitDays.Habit> habits = HabitDays.find(sessions, today);

        assertEquals(1, habits.size());
        assertEquals(DayOfWeek.SATURDAY, habits.get(0).weekday());
        assertEquals(60, habits.get(0).minutes());
        assertEquals(3, habits.get(0).weeks());
        assertTrue(HabitDays.find(List.of(sport("2026-10-03", 60), sport("2026-09-19", 60)), today).isEmpty());
        assertEquals(1, HabitDays.runs(sessions, today).get("STRENGTH").weeks());
    }

    private static HabitDays.SessionDay sport(String date, int minutes) {
        return new HabitDays.SessionDay(LocalDate.parse(date), SessionKind.SPORT, SportActivity.BADMINTON, minutes);
    }

    private static PlanProposalPlanner.Profile profile(WorkoutDifficulty level, int days, int minutes) {
        return new PlanProposalPlanner.Profile(level, days, minutes, Set.of());
    }
}
