package com.fitsync.workout.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutSetStatus;
import com.fitsync.workout.domain.WorkoutType;
import com.fitsync.workout.entity.WorkoutExerciseLogEntity;
import com.fitsync.workout.entity.WorkoutExerciseSetLogEntity;
import com.fitsync.workout.entity.WorkoutSessionEntity;

/**
 * Estimates how hard a finished workout was, from 1 to 10, with fixed rules that can each be
 * explained. It starts at 5, the person's usual, and moves by:
 * <ol>
 *   <li>the load against the median of their last same-kind workouts (needs three), by
 *       2.5 times log2 of the ratio, at most three either way;</li>
 *   <li>the difficulty of the exercises, weighted by the sets done: one less when all are beginner,
 *       one more when all are advanced;</li>
 *   <li>the rest actually taken against the plan: half a point more when it was a fifth shorter,
 *       half a point less when it was three tenths longer;</li>
 *   <li>one less when under six tenths of the sets were done, half a point more for a new best;</li>
 *   <li>without enough history, the length of the workout: half a point more from an hour, half a
 *       point less under twenty minutes.</li>
 * </ol>
 * It is an estimate: there is no heart rate behind it, so the person can always change it.
 */
public final class EffortEstimator {

    static final int MIN_HISTORY = 3;
    private static final double LOAD_WEIGHT = 2.5;
    private static final double LOAD_CAP = 3.0;

    private EffortEstimator() {
    }

    /** What a workout carried, in the one unit its done sets allow: kilograms moved, reps, or seconds. */
    public record Load(Unit unit, double amount) {
        public enum Unit { WEIGHT, REPS, SECONDS }
    }

    /**
     * The estimate, or null when no set was done.
     *
     * @param session   the session that was just finished
     * @param history   the loads of the person's earlier finished sessions of the same kind, newest first
     * @param newBest   whether this session set a personal record
     */
    public static Integer estimate(WorkoutSessionEntity session, List<Load> history, boolean newBest) {
        List<WorkoutExerciseSetLogEntity> done = new ArrayList<>();
        int total = 0;
        double difficultySum = 0;
        for (WorkoutExerciseLogEntity exercise : session.getExercises()) {
            for (WorkoutExerciseSetLogEntity set : exercise.getSets()) {
                total++;
                if (isDone(set)) {
                    done.add(set);
                    difficultySum += difficultyStep(exercise);
                }
            }
        }
        if (done.isEmpty()) {
            return null;
        }

        double score = 5.0;
        Load load = loadOf(session);
        List<Double> comparable = history.stream()
                .filter(prior -> prior != null && prior.unit() == load.unit() && prior.amount() > 0)
                .map(Load::amount)
                .toList();
        boolean enoughHistory = comparable.size() >= MIN_HISTORY && load.amount() > 0;
        if (enoughHistory) {
            double ratio = load.amount() / median(comparable);
            score += Math.max(-LOAD_CAP, Math.min(LOAD_CAP, LOAD_WEIGHT * Math.log(ratio) / Math.log(2)));
        }
        score += difficultySum / done.size();
        score += restAdjustment(done);
        if ((double) done.size() / total < 0.6) {
            score -= 1;
        }
        if (newBest) {
            score += 0.5;
        }
        if (!enoughHistory) {
            Integer minutes = session.getDurationMinutes();
            if (minutes != null && minutes >= 60) {
                score += 0.5;
            } else if (minutes != null && minutes < 20) {
                score -= 0.5;
            }
        }
        int rounded = BigDecimal.valueOf(score).setScale(0, RoundingMode.HALF_UP).intValue();
        return Math.max(1, Math.min(10, rounded));
    }

    /** The load of a session, in kilograms moved when any done set had a weight, else reps, else seconds. */
    public static Load loadOf(WorkoutSessionEntity session) {
        double weight = 0;
        double reps = 0;
        double seconds = 0;
        for (WorkoutExerciseLogEntity exercise : session.getExercises()) {
            for (WorkoutExerciseSetLogEntity set : exercise.getSets()) {
                if (!isDone(set)) {
                    continue;
                }
                int count = set.getReps() == null ? 0 : set.getReps();
                reps += count;
                if (set.getWeightKg() != null && set.getWeightKg().signum() > 0) {
                    weight += count * set.getWeightKg().doubleValue();
                }
                if (set.getDurationSeconds() != null) {
                    seconds += set.getDurationSeconds();
                }
            }
        }
        if (weight > 0) {
            return new Load(Load.Unit.WEIGHT, weight);
        }
        if (reps > 0) {
            return new Load(Load.Unit.REPS, reps);
        }
        if (seconds <= 0 && session.getDurationMinutes() != null) {
            seconds = session.getDurationMinutes() * 60.0;
        }
        return new Load(Load.Unit.SECONDS, seconds);
    }

    /** Calories burned, estimated from the kind of workout and its minutes; null without a duration. */
    public static Integer estimateCalories(WorkoutType type, Integer minutes) {
        if (minutes == null || minutes <= 0) {
            return null;
        }
        return Math.round(minutes * caloriesPerMinute(type));
    }

    private static float caloriesPerMinute(WorkoutType type) {
        if (type == null) {
            return 5;
        }
        return switch (type) {
            case HIIT -> 9;
            case CARDIO -> 8;
            case STRENGTH -> 6;
            case MOBILITY, YOGA -> 3;
            default -> 5;
        };
    }

    private static boolean isDone(WorkoutExerciseSetLogEntity set) {
        return set.getStatus() == WorkoutSetStatus.DONE;
    }

    private static double difficultyStep(WorkoutExerciseLogEntity exercise) {
        WorkoutDifficulty difficulty = exercise.getExercise() == null ? null : exercise.getExercise().getDifficulty();
        if (difficulty == WorkoutDifficulty.BEGINNER) {
            return -1;
        }
        return difficulty == WorkoutDifficulty.ADVANCED ? 1 : 0;
    }

    private static double restAdjustment(List<WorkoutExerciseSetLogEntity> done) {
        long taken = 0;
        long planned = 0;
        for (WorkoutExerciseSetLogEntity set : done) {
            if (set.getRestTakenSeconds() != null && set.getRestSeconds() != null && set.getRestSeconds() > 0) {
                taken += set.getRestTakenSeconds();
                planned += set.getRestSeconds();
            }
        }
        if (planned == 0) {
            return 0;
        }
        double ratio = (double) taken / planned;
        if (ratio < 0.8) {
            return 0.5;
        }
        return ratio > 1.3 ? -0.5 : 0;
    }

    private static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }
}
