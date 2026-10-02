package com.fitsync.nutrition.service;

import java.math.BigDecimal;
import java.util.Comparator;

/**
 * Ranks recipes by how well one serving fits what is left of today. The rules are plain arithmetic with no
 * model. A serving at or under the calories left comes before any serving over them. Inside the first group the
 * serving closest to one meal comes first, where one meal is a third of the daily target or the calories left,
 * whichever is smaller, so a single dish is never chosen to use up the whole day. A serving that adds to the
 * protein still missing, or to the nutrient furthest below its reference, earns a bonus of up to
 * {@value #MAX_BONUS} points for each, and a serving high in sugar loses the same amount.
 */
public final class RecipeFitScorer {

    static final double MAX_BONUS = 20;
    private static final double CLOSENESS_SCALE = 100;
    private static final double OVERSHOOT_FLOOR_KCAL = 200;
    /** A serving that gives half of the daily reference of the lowest nutrient earns the whole bonus. */
    private static final double FULL_NUTRIENT_SHARE = 0.5;

    private RecipeFitScorer() {
    }

    /** What today looks like; any part can be unknown. */
    public record Context(Integer kcalLeft, BigDecimal proteinShortGrams, String nutrientCode,
                          BigDecimal nutrientReference, Integer mealKcal) {

        /** A context without a meal size, where the calories left stand for one meal. */
        public Context(Integer kcalLeft, BigDecimal proteinShortGrams, String nutrientCode,
                       BigDecimal nutrientReference) {
            this(kcalLeft, proteinShortGrams, nutrientCode, nutrientReference, null);
        }
    }

    /** One serving of one recipe. nutrientAmount is the lowest nutrient in one serving, null when it has none. */
    public record Serving(int kcal, BigDecimal proteinGrams, BigDecimal nutrientAmount, boolean highSugar) {

        /** A serving that is not marked high in sugar. */
        public Serving(int kcal, BigDecimal proteinGrams, BigDecimal nutrientAmount) {
            this(kcal, proteinGrams, nutrientAmount, false);
        }
    }

    /** A ranking key; a lower tier is better, then a higher score is better. */
    public record Rank(int tier, double score) {

        /** The best first: the lower tier, then the higher score. */
        public static final Comparator<Rank> BEST_FIRST =
                Comparator.comparingInt(Rank::tier).thenComparing(Comparator.comparingDouble(Rank::score).reversed());
    }

    public static Rank rank(Context context, Serving serving) {
        int tier = 0;
        double closeness = 0;
        Integer left = context.kcalLeft();
        if (left != null) {
            if (serving.kcal() <= left) {
                int meal = context.mealKcal() == null ? left : Math.min(left, context.mealKcal());
                double distance = Math.abs(serving.kcal() - meal);
                closeness = CLOSENESS_SCALE * (1 - distance / Math.max(meal, OVERSHOOT_FLOOR_KCAL));
            } else {
                tier = 1;
                double over = serving.kcal() - left;
                // Going over has no lower bound, so that the lighter of two servings over the limit always wins.
                closeness = CLOSENESS_SCALE * (1 - over / Math.max(left, OVERSHOOT_FLOOR_KCAL));
            }
        }
        double sugarPenalty = serving.highSugar() ? MAX_BONUS : 0;
        return new Rank(tier, closeness + proteinBonus(context, serving) + nutrientBonus(context, serving)
                - sugarPenalty);
    }

    private static double proteinBonus(Context context, Serving serving) {
        BigDecimal shortfall = context.proteinShortGrams();
        if (shortfall == null || shortfall.signum() <= 0 || serving.proteinGrams() == null) {
            return 0;
        }
        double covered = serving.proteinGrams().doubleValue() / shortfall.doubleValue();
        return MAX_BONUS * Math.min(1, Math.max(0, covered));
    }

    private static double nutrientBonus(Context context, Serving serving) {
        BigDecimal reference = context.nutrientReference();
        if (context.nutrientCode() == null || reference == null || reference.signum() <= 0
                || serving.nutrientAmount() == null) {
            return 0;
        }
        double share = serving.nutrientAmount().doubleValue() / reference.doubleValue();
        return MAX_BONUS * Math.min(1, Math.max(0, share / FULL_NUTRIENT_SHARE));
    }
}
