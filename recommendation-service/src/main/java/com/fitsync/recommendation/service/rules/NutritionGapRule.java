package com.fitsync.recommendation.service.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.fitsync.recommendation.domain.GoalTypes;
import com.fitsync.recommendation.domain.UserTimeZones;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.springframework.stereotype.Service;

/**
 * R1 — daily nutrition gap: remaining kcal / protein for today. The calorie target is
 * the user's explicit CALORIES goal when set; otherwise a local TDEE estimate
 * (Mifflin–St Jeor from height + latest weight + activity level, with a neutral
 * age/sex assumption since the events carry neither — README §2 fact 3), adjusted by
 * the direction of a WEIGHT goal. Consumed numbers come from the context's running
 * today-totals and count only if they are for today in the user's timezone.
 */
@Service
public class NutritionGapRule implements Rule {

    static final int ASSUMED_AGE_YEARS = 30;
    // Midpoint of the Mifflin–St Jeor sex constants (+5 male / -161 female).
    static final int SEX_CONSTANT_MIDPOINT = -78;
    static final int WEIGHT_LOSS_DEFICIT_CALORIES = 500;
    static final int WEIGHT_GAIN_SURPLUS_CALORIES = 300;
    static final int MIN_DAILY_CALORIES = 1200;
    static final BigDecimal PROTEIN_GRAMS_PER_KG = new BigDecimal("1.6");

    static final String BASIS_CALORIE_GOAL = "CALORIE_GOAL";
    static final String BASIS_TDEE_ESTIMATE = "TDEE_ESTIMATE";

    private static final double DEFAULT_ACTIVITY_FACTOR = 1.4;
    private static final Map<String, Double> ACTIVITY_FACTORS = activityFactors();

    private final Clock clock;

    public NutritionGapRule(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String type() {
        return RecommendationTypes.NUTRITION_GAP;
    }

    @Override
    public Optional<RuleOutcome> evaluate(RecommendationContextEntity context) {
        Integer targetCalories = resolveTargetCalories(context);
        if (targetCalories == null) {
            return Optional.empty();
        }

        LocalDate today = LocalDate.now(clock.withZone(UserTimeZones.resolve(context.getTimezone())));
        boolean totalsAreForToday = today.equals(context.getTodayDate());
        int consumedCalories = totalsAreForToday && context.getTodayCaloriesConsumed() != null
                ? context.getTodayCaloriesConsumed()
                : 0;

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("basis", GoalTypes.isCalorieGoal(context.getCurrentGoalType())
                && context.getCurrentGoalTargetValue() != null
                ? BASIS_CALORIE_GOAL
                : BASIS_TDEE_ESTIMATE);
        facts.put("targetCalories", targetCalories);
        facts.put("consumedCalories", consumedCalories);
        facts.put("remainingCalories", targetCalories - consumedCalories);

        if (context.getLatestWeightKg() != null) {
            BigDecimal proteinTarget = context.getLatestWeightKg()
                    .multiply(PROTEIN_GRAMS_PER_KG)
                    .setScale(1, RoundingMode.HALF_UP);
            BigDecimal proteinConsumed = totalsAreForToday && context.getTodayProteinGrams() != null
                    ? context.getTodayProteinGrams()
                    : BigDecimal.ZERO;
            facts.put("proteinTargetGrams", proteinTarget);
            facts.put("proteinConsumedGrams", proteinConsumed);
            facts.put("proteinRemainingGrams", proteinTarget.subtract(proteinConsumed));
        }
        return Optional.of(new RuleOutcome(type(), facts));
    }

    private static Integer resolveTargetCalories(RecommendationContextEntity context) {
        if (GoalTypes.isCalorieGoal(context.getCurrentGoalType())
                && context.getCurrentGoalTargetValue() != null) {
            return context.getCurrentGoalTargetValue().intValue();
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
