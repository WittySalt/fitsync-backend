package com.fitsync.recommendation.service.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.fitsync.recommendation.domain.GoalTypes;
import com.fitsync.recommendation.domain.TodayIntake;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.springframework.stereotype.Service;

/**
 * R1 — daily nutrition gap: remaining kcal / protein for today. The calorie target is
 * the user's explicit CALORIES goal when set; otherwise the energy profile's TDEE from
 * user-service; otherwise a local TDEE estimate (Mifflin–St Jeor from height + latest
 * weight + activity level, with a neutral age/sex assumption since the events carry
 * neither — README §2 fact 3). The last two are adjusted by the direction of a WEIGHT
 * goal and never fall below {@link #MIN_DAILY_CALORIES}. The protein target is the user's explicit PROTEIN
 * goal when set, otherwise the body-weight estimate (weight × 1.6). Consumed numbers
 * come from the Nutrition Diary's daily summary, read by the caller for today in the user's
 * timezone. When the diary could not be read the rule states the targets only and no consumed
 * or remaining figure, rather than one that may be wrong.
 */
@Service
public class NutritionGapRule implements Rule {

    static final int ASSUMED_AGE_YEARS = 30;
    // Midpoint of the Mifflin–St Jeor sex constants (+5 male / -161 female).
    static final int SEX_CONSTANT_MIDPOINT = -78;
    static final int WEIGHT_LOSS_DEFICIT_CALORIES = 500;
    static final int WEIGHT_GAIN_SURPLUS_CALORIES = 300;
    /**
     * Safety floor for a derived target — also the coach guardrail's calorie floor and the
     * {@code minimumDailyCalories} fact handed to the coach Q&amp;A model.
     */
    public static final int MIN_DAILY_CALORIES = 1200;
    static final BigDecimal PROTEIN_GRAMS_PER_KG = new BigDecimal("1.6");

    static final String BASIS_CALORIE_GOAL = "CALORIE_GOAL";
    static final String BASIS_ENERGY_PROFILE = "ENERGY_PROFILE";
    static final String BASIS_TDEE_ESTIMATE = "TDEE_ESTIMATE";

    static final String BASIS_PROTEIN_GOAL = "PROTEIN_GOAL";
    static final String BASIS_BODY_WEIGHT = "BODY_WEIGHT";

    private static final double DEFAULT_ACTIVITY_FACTOR = 1.4;
    private static final Map<String, Double> ACTIVITY_FACTORS = activityFactors();

    @Override
    public String type() {
        return RecommendationTypes.NUTRITION_GAP;
    }

    @Override
    public Optional<RuleOutcome> evaluate(RecommendationContextEntity context) {
        return evaluate(context, TodayIntake.unknown());
    }

    @Override
    public Optional<RuleOutcome> evaluate(RecommendationContextEntity context, TodayIntake intake) {
        Integer targetCalories = resolveTargetCalories(context);
        if (targetCalories == null) {
            return Optional.empty();
        }

        boolean intakeKnown = intake != null && intake.known();

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("basis", basis(context));
        facts.put("targetCalories", targetCalories);
        if (intakeKnown) {
            int consumedCalories = intake.calories();
            facts.put("consumedCalories", consumedCalories);
            facts.put("remainingCalories", targetCalories - consumedCalories);
        }

        resolveProteinTarget(context).ifPresent(proteinTargetWithBasis -> {
            BigDecimal proteinTarget = proteinTargetWithBasis.targetGrams();
            facts.put("proteinBasis", proteinTargetWithBasis.basis());
            facts.put("proteinTargetGrams", proteinTarget);
            if (intakeKnown) {
                BigDecimal proteinConsumed = intake.proteinGrams() != null ? intake.proteinGrams() : BigDecimal.ZERO;
                facts.put("proteinConsumedGrams", proteinConsumed);
                facts.put("proteinRemainingGrams", proteinTarget.subtract(proteinConsumed));
            }
        });
        return Optional.of(new RuleOutcome(type(), facts));
    }

    /**
     * Protein target preference: the user's explicit PROTEIN goal when set, otherwise the
     * body-weight estimate (weight × 1.6). With a protein goal in place, protein facts
     * can appear even when {@code latestWeightKg} is null.
     */
    private static Optional<ProteinTarget> resolveProteinTarget(RecommendationContextEntity context) {
        if (context.getProteinTargetGrams() != null && context.getProteinTargetGrams().signum() > 0) {
            BigDecimal target = context.getProteinTargetGrams().setScale(1, RoundingMode.HALF_UP);
            return Optional.of(new ProteinTarget(target, BASIS_PROTEIN_GOAL));
        }
        if (context.getLatestWeightKg() != null) {
            BigDecimal target = context.getLatestWeightKg()
                    .multiply(PROTEIN_GRAMS_PER_KG)
                    .setScale(1, RoundingMode.HALF_UP);
            return Optional.of(new ProteinTarget(target, BASIS_BODY_WEIGHT));
        }
        return Optional.empty();
    }

    private record ProteinTarget(BigDecimal targetGrams, String basis) {
    }

    /**
     * Target preference: explicit calorie goal → EV-3 energy profile (exact TDEE from
     * user-service, adjusted by the weight-goal direction) → the local neutral-constant
     * estimate as the last resort.
     */
    private static Integer resolveTargetCalories(RecommendationContextEntity context) {
        if (hasCalorieGoal(context)) {
            return context.getCalorieGoalKcal();
        }
        if (context.getEnergyTargetCalories() != null && context.getLatestWeightKg() != null) {
            long target = context.getEnergyTargetCalories().longValue()
                    + goalAdjustment(context, context.getLatestWeightKg().doubleValue());
            return (int) Math.max(target, MIN_DAILY_CALORIES);
        }
        if (context.getHeightCm() == null || context.getLatestWeightKg() == null) {
            return null;
        }
        double weightKg = context.getLatestWeightKg().doubleValue();
        double heightCm = context.getHeightCm().doubleValue();
        double bmr = 10.0 * weightKg + 6.25 * heightCm - 5.0 * ASSUMED_AGE_YEARS + SEX_CONSTANT_MIDPOINT;
        double tdee = bmr * activityFactor(context.getActivityLevel());
        long target = Math.round(tdee) + goalAdjustment(context, weightKg);
        return (int) Math.max(target, MIN_DAILY_CALORIES);
    }

    private static boolean hasCalorieGoal(RecommendationContextEntity context) {
        return context.getCalorieGoalKcal() != null && context.getCalorieGoalKcal() > 0;
    }

    private static String basis(RecommendationContextEntity context) {
        if (hasCalorieGoal(context)) {
            return BASIS_CALORIE_GOAL;
        }
        if (context.getEnergyTargetCalories() != null && context.getLatestWeightKg() != null) {
            return BASIS_ENERGY_PROFILE;
        }
        return BASIS_TDEE_ESTIMATE;
    }

    private static int goalAdjustment(RecommendationContextEntity context, double weightKg) {
        if (!GoalTypes.isWeightGoal(context.getCurrentGoalType())
                || context.getCurrentGoalTargetValue() == null) {
            return 0;
        }
        double targetWeightKg = context.getCurrentGoalTargetValue().doubleValue();
        if (targetWeightKg < weightKg) {
            return -WEIGHT_LOSS_DEFICIT_CALORIES;
        }
        if (targetWeightKg > weightKg) {
            return WEIGHT_GAIN_SURPLUS_CALORIES;
        }
        return 0;
    }

    private static double activityFactor(String activityLevel) {
        if (activityLevel == null) {
            return DEFAULT_ACTIVITY_FACTOR;
        }
        Double factor = ACTIVITY_FACTORS.get(activityLevel);
        return factor != null ? factor.doubleValue() : DEFAULT_ACTIVITY_FACTOR;
    }

    private static Map<String, Double> activityFactors() {
        Map<String, Double> factors = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        factors.put("SEDENTARY", 1.2);
        factors.put("LIGHT", 1.375);
        factors.put("MODERATE", 1.55);
        factors.put("ACTIVE", 1.725);
        factors.put("VERY_ACTIVE", 1.9);
        return factors;
    }
}
