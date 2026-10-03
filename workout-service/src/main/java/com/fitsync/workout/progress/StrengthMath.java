package com.fitsync.workout.progress;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

import com.fitsync.workout.dto.progress.TrainingProgressView.Level;
import com.fitsync.workout.dto.progress.TrainingProgressView.Relative;

/** The estimated best single, percent changes and the relative-strength ladder. */
public final class StrengthMath {

    /** Body weights accepted from the client for relative strength, inclusive. */
    public static final BigDecimal MIN_BODY_WEIGHT_KG = BigDecimal.valueOf(30);
    public static final BigDecimal MAX_BODY_WEIGHT_KG = BigDecimal.valueOf(300);

    private static final BigDecimal THIRTY = BigDecimal.valueOf(30);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private StrengthMath() {
    }

    /** e1RM = weight × (1 + reps / 30), kept at four decimals until it is shown. */
    public static BigDecimal e1rm(BigDecimal weightKg, int reps) {
        return weightKg.multiply(BigDecimal.valueOf(30L + reps)).divide(THIRTY, 4, RoundingMode.HALF_UP);
    }

    /** One decimal, half up: how every kilogram figure is sent. */
    public static BigDecimal round1(BigDecimal value) {
        return value == null ? null : value.setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * The percent change from first to last, rounded half down as on every board (12.5% shows as 12%):
     * ceil(x − 0.5). Zero when there is no first value to divide by.
     */
    public static int pctChange(BigDecimal first, BigDecimal last) {
        if (first == null || last == null || first.signum() == 0) {
            return 0;
        }
        BigDecimal pct = last.divide(first, 8, RoundingMode.HALF_UP).subtract(BigDecimal.ONE).multiply(HUNDRED);
        return pct.subtract(HALF).setScale(0, RoundingMode.CEILING).intValue();
    }

    /** The unrounded percent change, for rules that compare against a threshold. */
    public static double rawPct(BigDecimal first, BigDecimal last) {
        if (first == null || last == null || first.signum() == 0) {
            return 0;
        }
        return last.divide(first, 8, RoundingMode.HALF_UP).subtract(BigDecimal.ONE).doubleValue() * 100;
    }

    /** A body weight in the accepted range, else null (no ratios). */
    public static BigDecimal acceptedBodyWeight(BigDecimal bodyWeightKg) {
        if (bodyWeightKg == null || bodyWeightKg.compareTo(MIN_BODY_WEIGHT_KG) < 0
                || bodyWeightKg.compareTo(MAX_BODY_WEIGHT_KG) > 0) {
            return null;
        }
        return bodyWeightKg;
    }

    /**
     * The ladder of a lift as four ratios of body weight (contract 1.1). The first number is the
     * left end of the track; novice starts at the second, intermediate at the third and advanced
     * at the fourth, as drawn on board 18a. Below the second number is beginner.
     */
    static BigDecimal[] rungs(String exerciseName) {
        String name = exerciseName == null ? "" : exerciseName.toLowerCase(Locale.ROOT);
        if (name.contains("deadlift") && !name.contains("romanian") && !name.contains("stiff")) {
            return ratios("1.0", "1.25", "1.5", "2.0");
        }
        if (name.contains("bench press")) {
            return ratios("0.5", "0.75", "1.0", "1.5");
        }
        if (name.contains("overhead press") || name.contains("military press") || name.contains("shoulder press")) {
            return ratios("0.35", "0.55", "0.75", "1.0");
        }
        if (name.contains("squat") && !name.contains("split")) {
            return ratios("0.75", "1.0", "1.25", "1.75");
        }
        return ratios("0.5", "0.75", "1.0", "1.5");
    }

    /** The ratio, the level, the next level and the e1RM in kg (to the nearest 0.5) that reaches it. */
    public static Relative relative(String exerciseName, BigDecimal e1rmKg, BigDecimal bodyWeightKg) {
        if (e1rmKg == null || bodyWeightKg == null) {
            return null;
        }
        BigDecimal ratio = e1rmKg.divide(bodyWeightKg, 2, RoundingMode.HALF_UP);
        BigDecimal exact = e1rmKg.divide(bodyWeightKg, 6, RoundingMode.HALF_UP);
        BigDecimal[] rungs = rungs(exerciseName);
        Level[] levels = Level.values();
        int index = 0;
        for (int i = 1; i < rungs.length; i++) {
            if (exact.compareTo(rungs[i]) >= 0) {
                index = i;
            }
        }
        Level level = levels[index];
        if (index == levels.length - 1) {
            return new Relative(ratio, level, null, null);
        }
        BigDecimal nextAt = rungs[index + 1].multiply(bodyWeightKg).multiply(TWO)
                .setScale(0, RoundingMode.HALF_UP).divide(TWO, 1, RoundingMode.HALF_UP);
        return new Relative(ratio, level, levels[index + 1], nextAt);
    }

    private static BigDecimal[] ratios(String... values) {
        BigDecimal[] out = new BigDecimal[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = new BigDecimal(values[i]);
        }
        return out;
    }
}
