package com.fitsync.workout.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;

import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutSetStatus;
import com.fitsync.workout.domain.WorkoutType;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.entity.SessionEntry;
import com.fitsync.workout.entity.WorkoutExerciseSetLogEntity;
import com.fitsync.workout.entity.WorkoutSessionEntity;
import com.fitsync.workout.service.EffortEstimator.Load;
import org.junit.jupiter.api.Test;

class EffortEstimatorTests {

    /** A session of one exercise; each set is {reps, kg, status, planned rest, rest taken}. */
    private static WorkoutSessionEntity session(WorkoutDifficulty difficulty, Integer minutes, Object[]... sets) {
        ExerciseEntity exercise = new ExerciseEntity();
        exercise.setDifficulty(difficulty);
        SessionEntry log = new SessionEntry();
        log.setExercise(exercise);
        int number = 1;
        for (Object[] values : sets) {
            WorkoutExerciseSetLogEntity set = new WorkoutExerciseSetLogEntity();
            set.setSetNumber(number++);
            set.setReps((Integer) values[0]);
            set.setWeightKg(values[1] == null ? null : new BigDecimal(values[1].toString()));
            set.setStatus((WorkoutSetStatus) values[2]);
            set.setRestSeconds((Integer) values[3]);
            set.setRestTakenSeconds((Integer) values[4]);
            log.getSets().add(set);
        }
        WorkoutSessionEntity session = new WorkoutSessionEntity();
        session.setDurationMinutes(minutes);
        session.getEntries().add(log);
        return session;
    }

    private static Object[] done(int reps, double kg) {
        return new Object[] {reps, kg, WorkoutSetStatus.DONE, null, null};
    }

    private static Object[] rested(int reps, double kg, int planned, int taken) {
        return new Object[] {reps, kg, WorkoutSetStatus.DONE, planned, taken};
    }

    private static Object[] open() {
        return new Object[] {null, null, WorkoutSetStatus.PENDING, null, null};
    }

    private static List<Load> usual(double kilograms) {
        return List.of(new Load(Load.Unit.WEIGHT, kilograms), new Load(Load.Unit.WEIGHT, kilograms),
                new Load(Load.Unit.WEIGHT, kilograms), new Load(Load.Unit.WEIGHT, kilograms));
    }

    @Test
    void givesFiveForAUsualWorkoutOfIntermediateMoves() {
        // 3 x 10 x 20 kg = 600 kg, the same as the usual.
        WorkoutSessionEntity session = session(WorkoutDifficulty.INTERMEDIATE, 40, done(10, 20), done(10, 20), done(10, 20));

        assertEquals(5, EffortEstimator.estimate(session, usual(600), false));
    }

    @Test
    void movesByTheLoadAgainstTheUsualAndStopsAtThreeEitherWay() {
        WorkoutSessionEntity doubled = session(WorkoutDifficulty.INTERMEDIATE, 40, done(10, 40), done(10, 40), done(10, 40));
        WorkoutSessionEntity halved = session(WorkoutDifficulty.INTERMEDIATE, 40, done(10, 10), done(10, 10), done(10, 10));
        WorkoutSessionEntity eightfold = session(WorkoutDifficulty.INTERMEDIATE, 40, done(10, 160), done(10, 160), done(10, 160));

        assertEquals(8, EffortEstimator.estimate(doubled, usual(600), false));
        assertEquals(3, EffortEstimator.estimate(halved, usual(600), false));
        assertEquals(8, EffortEstimator.estimate(eightfold, usual(600), false));
    }

    @Test
    void takesTheDifficultyOfTheMovesAndANewBest() {
        WorkoutSessionEntity beginner = session(WorkoutDifficulty.BEGINNER, 40, done(10, 20), done(10, 20), done(10, 20));
        WorkoutSessionEntity advanced = session(WorkoutDifficulty.ADVANCED, 40, done(10, 20), done(10, 20), done(10, 20));

        assertEquals(4, EffortEstimator.estimate(beginner, usual(600), false));
        assertEquals(6, EffortEstimator.estimate(advanced, usual(600), false));
        assertEquals(7, EffortEstimator.estimate(advanced, usual(600), true));
    }

    @Test
    void takesTheRestAgainstThePlanAndTheShareOfSetsDone() {
        WorkoutSessionEntity shortRests = session(WorkoutDifficulty.ADVANCED, 40,
                rested(10, 20, 75, 40), rested(10, 20, 75, 40), rested(10, 20, 75, 40));
        WorkoutSessionEntity longRests = session(WorkoutDifficulty.ADVANCED, 40,
                rested(10, 20, 75, 120), rested(10, 20, 75, 120), rested(10, 20, 75, 120));
        // Two of four sets done, at the usual load: one less, so 4.
        WorkoutSessionEntity halfDone = session(WorkoutDifficulty.INTERMEDIATE, 40, done(10, 30), done(10, 30), open(), open());

        assertEquals(7, EffortEstimator.estimate(shortRests, usual(600), false));
        assertEquals(6, EffortEstimator.estimate(longRests, usual(600), false));
        assertEquals(4, EffortEstimator.estimate(halfDone, usual(600), false));
    }

    @Test
    void usesTheLengthWhileThereAreFewerThanThreeEarlierWorkoutsOfTheKind() {
        WorkoutSessionEntity longOne = session(WorkoutDifficulty.INTERMEDIATE, 75, done(10, 20));
        WorkoutSessionEntity shortOne = session(WorkoutDifficulty.INTERMEDIATE, 15, done(10, 20));
        List<Load> two = List.of(new Load(Load.Unit.WEIGHT, 100), new Load(Load.Unit.WEIGHT, 100));

        assertEquals(6, EffortEstimator.estimate(longOne, two, false));
        assertEquals(5, EffortEstimator.estimate(shortOne, List.of(), false));
        assertEquals(5, EffortEstimator.estimate(session(WorkoutDifficulty.INTERMEDIATE, 30, done(10, 20)), two, false));
    }

    @Test
    void comparesOnlyLoadsOfTheSameUnitAndGivesNothingWithoutADoneSet() {
        WorkoutSessionEntity bodyweight = session(WorkoutDifficulty.INTERMEDIATE, 40,
                new Object[] {20, null, WorkoutSetStatus.DONE, null, null});
        List<Load> repsUsual = List.of(new Load(Load.Unit.REPS, 10), new Load(Load.Unit.REPS, 10),
                new Load(Load.Unit.REPS, 10), new Load(Load.Unit.WEIGHT, 900));

        assertEquals(Load.Unit.REPS, EffortEstimator.loadOf(bodyweight).unit());
        assertEquals(8, EffortEstimator.estimate(bodyweight, repsUsual, false));
        assertNull(EffortEstimator.estimate(session(WorkoutDifficulty.INTERMEDIATE, 40, open()), usual(600), false));
    }

    @Test
    void estimatesCaloriesFromTheKindAndTheMinutes() {
        assertEquals(180, EffortEstimator.estimateCalories(WorkoutType.STRENGTH, 30));
        assertEquals(240, EffortEstimator.estimateCalories(WorkoutType.CARDIO, 30));
        assertEquals(90, EffortEstimator.estimateCalories(WorkoutType.MOBILITY, 30));
        assertEquals(150, EffortEstimator.estimateCalories(null, 30));
        assertNull(EffortEstimator.estimateCalories(WorkoutType.STRENGTH, 0));
        assertNull(EffortEstimator.estimateCalories(WorkoutType.STRENGTH, null));
    }
}
