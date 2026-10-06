package com.fitsync.nutrition.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;

/**
 * Ranks recipes by how well one serving fits what is left of today. The rules are plain arithmetic with no
 * model, and both the "Fits today" sort of the Library and the Cook for tonight card use this one class, so
 * the order and the card cannot disagree.
 *
 * <p>The budget rule gives the full {@value #BUDGET_POINTS} points to a serving within 15 % of one meal, where
 * one meal is the daily target divided by three or the calories left, whichever is smaller, and falls
 * linearly to zero at twice that distance from the target. A serving over the calories left always comes
 * behind every serving under them. A serving that adds to the protein still missing, or to the nutrient
 * furthest below its reference, earns a bonus of up to {@value #MAX_BONUS} points for each, and a serving high
 * in sugar loses the same amount as the protein bonus. A recipe cooked or logged in the last seven days moves
 * down by one tier and never out. Equal scores fall back to the source order, the person's own recipes, then
 * the recent ones, then the Library.
 */
public final class RecipeSuggestionRanking {

    static final double MAX_BONUS = 20;
    static final double BUDGET_POINTS = 100;
    /** A serving within this share of one meal scores the full budget points. */
    static final double BAND = 0.15;
    private static final double OVERSHOOT_FLOOR_KCAL = 200;
    /** A serving that gives half of the daily reference of the lowest nutrient earns the whole bonus. */
    private static final double FULL_NUTRIENT_SHARE = 0.5;
    /** The share of the protein bonus that counts as closing most of the gap. */
    static final double MOST_OF_THE_GAP = 0.6;

    private RecipeSuggestionRanking() {
    }

    /** Where a candidate comes from; the declaration order is the order on ties. */
    public enum Source { MINE, RECENT, LIBRARY }

    /** What today looks like; any part can be unknown. */
    public record Context(Integer kcalLeft, BigDecimal proteinShortGrams, String nutrientCode,
                          String nutrientName, BigDecimal nutrientReference, Integer mealKcal) {

        /** A context without a meal size, where the calories left stand for one meal. */
        public Context(Integer kcalLeft, BigDecimal proteinShortGrams, String nutrientCode,
                       BigDecimal nutrientReference) {
            this(kcalLeft, proteinShortGrams, nutrientCode, nutrientCode, nutrientReference, null);
        }
    }

    /**
     * One serving of one recipe. nutrientAmount is the lowest nutrient in one serving, null when it has none.
     * cooksThisMonth counts cook records of the last 30 days and recentlyCooked says a cook or a logged meal
     * fell in the last seven days.
     */
    public record Serving(int kcal, BigDecimal proteinGrams, BigDecimal nutrientAmount, boolean highSugar,
                          Source source, boolean recentlyCooked, int cooksThisMonth) {

        public Serving(int kcal, BigDecimal proteinGrams, BigDecimal nutrientAmount) {
            this(kcal, proteinGrams, nutrientAmount, false, Source.LIBRARY, false, 0);
        }
    }

    /** A ranking key; a lower tier is better, then a higher score, then the earlier source. */
    public record Rank(int tier, double score, int sourceOrder) {

        /** The best first. */
        public static final Comparator<Rank> BEST_FIRST = Comparator.comparingInt(Rank::tier)
                .thenComparing(Comparator.comparingDouble(Rank::score).reversed())
                .thenComparingInt(Rank::sourceOrder);
    }

    /** The points each rule gave one serving. */
    private record Points(boolean over, double budget, double protein, double nutrient, double sugar) {

        double total() {
            return budget + protein + nutrient - sugar;
        }
    }

    public static Rank rank(Context context, Serving serving) {
        Points points = points(context, serving);
        int tier = (points.over() ? 2 : 0) + (serving.recentlyCooked() ? 1 : 0);
        double score = BigDecimal.valueOf(points.total()).setScale(2, RoundingMode.HALF_UP).doubleValue();
        return new Rank(tier, score, serving.source().ordinal());
    }

    private static Points points(Context context, Serving serving) {
        Integer left = context.kcalLeft();
        boolean over = left != null && serving.kcal() > left;
        double budget = 0;
        if (over) {
            // Going over has no lower bound, so that the lighter of two servings over the limit always wins.
            double excess = serving.kcal() - left;
            budget = BUDGET_POINTS * (1 - excess / Math.max(left, OVERSHOOT_FLOOR_KCAL));
        } else {
            Double meal = mealSize(context);
            if (meal != null) {
                double band = BAND * meal;
                double distance = Math.abs(serving.kcal() - meal);
                budget = distance <= band ? BUDGET_POINTS
                        : Math.max(0, BUDGET_POINTS * (1 - (distance - band) / band));
            }
        }
        double protein = proteinBonus(context, serving);
        return new Points(over, budget, protein, nutrientBonus(context, serving),
                serving.highSugar() ? MAX_BONUS : 0);
    }

    /** One meal, in kcal: the smaller of the calories left and the meal size, or null when neither is known. */
    static Double mealSize(Context context) {
        Integer left = context.kcalLeft();
        Integer meal = context.mealKcal();
        if (left == null && meal == null) {
            return null;
        }
        int size;
        if (left == null) {
            size = meal;
        } else if (meal == null) {
            size = left;
        } else {
            size = Math.min(left, meal);
        }
        return (double) Math.max(size, 1);
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

    /**
     * One sentence that names the rule that gave the most points. A serving that fits the budget and closes most
     * of the protein gap says both. A recipe that earned nothing from the three rules says how often it was
     * cooked this month when it was, and otherwise a plain sentence.
     */
    public static String reason(Context context, Serving serving) {
        Points points = points(context, serving);
        boolean fits = context.kcalLeft() != null && !points.over() && points.budget() >= BUDGET_POINTS;
        boolean closesGap = points.protein() >= MOST_OF_THE_GAP * MAX_BONUS;
        if (fits && closesGap) {
            return "Fits your " + context.kcalLeft() + " left and closes most of the protein gap";
        }
        double budget = context.kcalLeft() != null && !points.over() ? points.budget() : 0;
        double protein = points.protein();
        double nutrient = points.nutrient();
        // The budget wins a tie with the other rules, and the protein wins a tie with the nutrient.
        if (budget > 0 && budget >= protein && budget >= nutrient) {
            return "Fits your " + context.kcalLeft() + " left";
        }
        if (protein > 0 && protein >= nutrient) {
            return closesGap ? "Closes most of the protein gap"
                    : "Adds " + whole(serving.proteinGrams()) + " g of protein";
        }
        if (nutrient > 0) {
            return "Brings " + context.nutrientName() + ", your lowest this week";
        }
        if (serving.cooksThisMonth() > 0) {
            String times = serving.cooksThisMonth() == 1 ? "once" : serving.cooksThisMonth() + " times";
            return "You cooked it " + times + " this month";
        }
        return "A balanced option for tonight";
    }

    private static String whole(BigDecimal grams) {
        return grams.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
}
