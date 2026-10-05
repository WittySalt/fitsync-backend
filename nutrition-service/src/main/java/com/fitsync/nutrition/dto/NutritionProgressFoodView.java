package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What only nutrition knows for the Progress Food page (progress-redesign contract section 3.1). The client
 * merges it with analytics' {@code /analytics/progress/food}.
 *
 * @param mealTimes       per meal type with meals in the period: the median and quartiles of the minute of the
 *                        day each meal was eaten, in the caller's zone
 * @param topFoods        the five foods logged most often in the period
 * @param cooks           recipes cooked in the period and the newest cook
 * @param recentWindow    the last 31 full days of the period at most, the range the nutrient summary allows; it
 *                        is the range of {@code lowestNutrients} and {@code habits}. Today is still going and is
 *                        never in it, so it ends yesterday when the period reaches today; when the period holds
 *                        no full day its {@code to} is the day before {@code from} and both summaries are empty
 * @param lowestNutrients the three vitamins or minerals with the smallest share of their daily reference
 * @param habits          in {@code recentWindow}: logged days, days that met the fibre target, and days whose
 *                        plain water and drinks together reached the daily water goal
 */
public record NutritionProgressFoodView(
        LocalDate from,
        LocalDate to,
        List<MealTime> mealTimes,
        List<TopFood> topFoods,
        Cooks cooks,
        Window recentWindow,
        List<LowNutrient> lowestNutrients,
        Habits habits) {

    public NutritionProgressFoodView {
        mealTimes = List.copyOf(mealTimes);
        topFoods = List.copyOf(topFoods);
        lowestNutrients = List.copyOf(lowestNutrients);
    }

    @Override
    public List<MealTime> mealTimes() {
        return List.copyOf(mealTimes);
    }

    @Override
    public List<TopFood> topFoods() {
        return List.copyOf(topFoods);
    }

    @Override
    public List<LowNutrient> lowestNutrients() {
        return List.copyOf(lowestNutrients);
    }

    /** Minutes after local midnight; p25 and p75 interpolate between neighbouring meals. */
    public record MealTime(String mealType, int medianMinuteOfDay, int p25, int p75, int count) {
    }

    /** foodCatalogItemId is null for a custom or typed-in food; foodGroup is OTHER without a known category. */
    public record TopFood(UUID foodCatalogItemId, String name, String foodGroup, String categoryCode, long times) {
    }

    public record Cooks(long count, LastCook last) {
    }

    /** name is null when the recipe is no longer readable. */
    public record LastCook(UUID recipeId, String name, Instant finishedAt) {
    }

    public record Window(LocalDate from, LocalDate to) {
    }

    /** share is the average divided by the reference, as on the nutrient summary. */
    public record LowNutrient(String code, String displayName, BigDecimal share) {
    }

    /**
     * waterGoalMl is the daily goal from the preferences, null when none is known. A day without a known goal
     * is never met and is left out of waterDays, the denominator, so both are 0 without a goal. waterDays
     * counts the logged days of the window, as fibre does.
     */
    public record Habits(int loggedDays, int fibreMetDays, int waterMetDays, int waterDays, Integer waterGoalMl) {
    }
}
