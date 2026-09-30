package com.fitsync.recommendation.coach.reading;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Turns one nutrition-service page insight into the numbered facts a nutrition reading is built and
 * checked against.
 *
 * <p>Pure and deterministic, like {@link ReadingFactsBuilder}: a fact is present when its number is,
 * and every figure is formatted here so that a sentence can only quote a string the validator can
 * find. The meal shares are shares of the day's protein, worked out once here so no sentence has to
 * do the arithmetic.
 */
@Component
public class NutritionFactsBuilder {

    private static final String BREAKFAST = "BREAKFAST";
    private static final String LUNCH = "LUNCH";
    private static final String DINNER = "DINNER";

    public List<ReadingFact> build(NutritionInsight insight) {
        List<ReadingFact> chosen = new ArrayList<>();
        addProtein(chosen, insight);
        addMealProtein(chosen, insight, "PROTEIN_BREAKFAST", "Protein at breakfast", BREAKFAST);
        addMealProtein(chosen, insight, "PROTEIN_LUNCH_DINNER", "Protein at lunch and dinner", LUNCH, DINNER);
        ReadingFact calories = caloriesFact(insight);
        if (calories != null) {
            chosen.add(calories);
        }
        return ReadingFactsBuilder.numbered(chosen);
    }

    private static void addProtein(List<ReadingFact> facts, NutritionInsight insight) {
        BigDecimal protein = insight.proteinPerDayGrams();
        if (protein == null) {
            return;
        }
        BigDecimal earlier = insight.earlierProteinPerDayGrams();
        String earlierPart = earlier == null ? null : ReadingFactsBuilder.upToOneDecimal(earlier) + " g the week before";
        BigDecimal target = insight.proteinTargetGrams();
        String targetPart = target == null ? null : "target " + ReadingFactsBuilder.upToOneDecimal(target) + " g";
        facts.add(new ReadingFact(0, "PROTEIN_PER_DAY", "Protein a day",
                ReadingFactsBuilder.upToOneDecimal(protein), "g", protein,
                earlier == null ? "" : ReadingFactsBuilder.percentChangeNote(protein, earlier),
                joinParts(earlierPart, targetPart)));
    }

    /** Adds a fact for the given meal types together, or nothing when none of them has an entry. */
    private static void addMealProtein(List<ReadingFact> facts, NutritionInsight insight, String key,
                                       String label, String... mealTypes) {
        BigDecimal grams = null;
        for (NutritionInsight.MealProtein meal : insight.proteinByMeal()) {
            if (meal == null || meal.gramsPerDay() == null || meal.mealType() == null) {
                continue;
            }
            for (String wanted : mealTypes) {
                if (String.CASE_INSENSITIVE_ORDER.compare(wanted, meal.mealType()) == 0) {
                    grams = grams == null ? meal.gramsPerDay() : grams.add(meal.gramsPerDay());
                }
            }
        }
        if (grams == null) {
            return;
        }
        facts.add(new ReadingFact(0, key, label, ReadingFactsBuilder.upToOneDecimal(grams), "g", grams,
                shareNote(grams, insight.proteinPerDayGrams()), ""));
    }

    /** The share of the day's protein as a whole percent, or nothing when the day's total is unknown. */
    private static String shareNote(BigDecimal grams, BigDecimal dayTotal) {
        if (dayTotal == null || dayTotal.signum() <= 0) {
            return "";
        }
        int percent = grams.multiply(BigDecimal.valueOf(100))
                .divide(dayTotal, 0, RoundingMode.HALF_UP)
                .intValueExact();
        return percent + "% of the day";
    }

    /**
     * The calories fact, unnumbered, or null when the insight has no calorie figure to report. The
     * progress page borrows it, so it lives here beside the rest of the nutrition wording.
     */
    static ReadingFact caloriesFact(NutritionInsight insight) {
        if (insight == null || insight.isNone() || insight.caloriesPerDay() == null) {
            return null;
        }
        BigDecimal calories = insight.caloriesPerDay();
        BigDecimal earlier = insight.earlierCaloriesPerDay();
        String earlierPart = earlier == null
                ? null
                : ReadingFactsBuilder.wholeWithCommas(earlier) + " kcal the week before";
        BigDecimal target = insight.calorieTargetKcal();
        String targetPart = target == null
                ? null
                : "target " + ReadingFactsBuilder.wholeWithCommas(target) + " kcal";
        return new ReadingFact(0, "CALORIES_PER_DAY", "Calories a day",
                ReadingFactsBuilder.wholeWithCommas(calories), "kcal", calories,
                earlier == null ? "" : ReadingFactsBuilder.percentChangeNote(calories, earlier),
                joinParts(earlierPart, targetPart));
    }

    private static String joinParts(String first, String second) {
        if (first == null) {
            return second == null ? "" : second;
        }
        return second == null ? first : first + "; " + second;
    }
}
