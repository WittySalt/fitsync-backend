package com.fitsync.recommendation.coach.reading;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Full, demo-shaped nutrition and progress insights shared by the tests of the two newer pages. */
final class PageInsightFixtures {

    private PageInsightFixtures() {
    }

    /** Protein 163.8 g up from 162.2, four meals, calories 2,173 up from 2,153, both targets set. */
    static NutritionInsight nutrition() {
        return nutrition(new BigDecimal("163.8"), new BigDecimal("162.2"), new BigDecimal("2173"),
                new BigDecimal("2153"), meals("30.3", "59.9", "50.0", "23.6"));
    }

    static NutritionInsight nutrition(BigDecimal protein, BigDecimal earlierProtein, BigDecimal calories,
                                      BigDecimal earlierCalories, List<NutritionInsight.MealProtein> meals) {
        return new NutritionInsight("Asia/Singapore", "PREVIOUS_WEEK",
                LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-27"), 7,
                protein, earlierProtein, new BigDecimal("140"),
                calories, earlierCalories, new BigDecimal("2100"), meals);
    }

    static NutritionInsight noNutrition() {
        return new NutritionInsight("Asia/Singapore", "NONE", null, null, 0,
                null, null, null, null, null, null, List.of());
    }

    static List<NutritionInsight.MealProtein> meals(String breakfast, String lunch, String dinner, String snack) {
        return List.of(
                new NutritionInsight.MealProtein("BREAKFAST", "Breakfast", new BigDecimal(breakfast)),
                new NutritionInsight.MealProtein("LUNCH", "Lunch", new BigDecimal(lunch)),
                new NutritionInsight.MealProtein("DINNER", "Dinner", new BigDecimal(dinner)),
                new NutritionInsight.MealProtein("SNACK", "Snack", new BigDecimal(snack)));
    }

    /** Weight 71.1 kg on 28 September, 16 weigh-ins, losing 0.21 kg a week, goal 68 by 9 December. */
    static ProgressInsight progress() {
        return progress(new BigDecimal("-0.21"), LocalDate.parse("2026-12-09"), LocalDate.parse("2027-01-10"));
    }

    static ProgressInsight progress(BigDecimal rate, LocalDate goalDate, LocalDate projected) {
        return new ProgressInsight("Asia/Singapore", LocalDate.parse("2026-10-01"), new BigDecimal("71.1"),
                LocalDate.parse("2026-09-28"), 16, rate, 28, new BigDecimal("68.0"), goalDate, projected);
    }

    static ProgressInsight progressWithoutGoal(BigDecimal rate) {
        return new ProgressInsight("Asia/Singapore", LocalDate.parse("2026-10-01"), new BigDecimal("71.1"),
                LocalDate.parse("2026-09-28"), 16, rate, 28, null, null, null);
    }

    static ProgressInsight noWeight() {
        return new ProgressInsight("Asia/Singapore", LocalDate.parse("2026-10-01"), null, null, 0,
                null, null, null, null, null);
    }
}
