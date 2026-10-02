package com.fitsync.analytics.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Energy this week (contract section 11.2). Energy used is resting energy (BMR x 1.2) plus the
 * logged workout calories of the day, never a full TDEE on top of exercise.
 *
 * @param status       OK, NO_PROFILE (no BMR is known) or TOO_FEW_DAYS (fewer than 3 logged days)
 * @param daysLogged   days of the week with at least one meal
 * @param bmrKcal      the BMR valid on the last day of the week, null without a profile
 * @param average      per logged day, null when no day is logged
 * @param mealSplit    average per logged day, null when no day is logged
 * @param weeklyKgEquivalent average balance x 7 / 7700, only when the status is OK
 */
public record AnalyticsEnergyWeeklyResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        String status,
        int daysLogged,
        Integer bmrKcal,
        boolean bmrEstimated,
        double restingFactor,
        Average average,
        MealSplit mealSplit,
        Double weeklyKgEquivalent,
        List<Day> days,
        List<Week> weeks) {

    public AnalyticsEnergyWeeklyResponse {
        days = List.copyOf(days);
        weeks = List.copyOf(weeks);
    }

    @Override
    public List<Day> days() {
        return List.copyOf(days);
    }

    @Override
    public List<Week> weeks() {
        return List.copyOf(weeks);
    }

    /** Per logged day, whole kcal. {@code balance} is eaten minus used. Resting, used and balance are null without a BMR. */
    public record Average(Integer eaten, Integer resting, Integer workouts, Integer used, Integer balance) {
    }

    /** Average kcal per logged day by meal type. Meals stored before the type was kept count as other. */
    public record MealSplit(int breakfast, int lunch, int dinner, int snack, int other) {
    }

    /**
     * Eaten, balance and runningTotal are null on a day without a logged meal. Today (still going) shows
     * what is logged so far but has no balance or running total and is left out of every count.
     */
    public record Day(
            LocalDate date,
            boolean logged,
            boolean today,
            Integer eaten,
            Integer resting,
            Integer workouts,
            boolean workoutsEstimated,
            Integer balance,
            Integer runningTotal) {
    }

    /** Cumulative from the first of the eight weeks. */
    public record Week(LocalDate weekStart, Double expectedKgChange, Double actualKgChange) {
    }
}
