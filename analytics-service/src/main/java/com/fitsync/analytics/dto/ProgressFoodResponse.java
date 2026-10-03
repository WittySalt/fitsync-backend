package com.fitsync.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The Food page of Progress, the part analytics knows (contract section 2.2). A logged day is a day with at
 * least one meal in the daily nutrition rows; the energy figures keep the energy view's own rule (two meals,
 * today left out). The client merges this with nutrition's {@code /nutrition/progress/food}.
 */
public record ProgressFoodResponse(
        ProgressPeriod period,
        ProgressPeriod previous,
        Hero hero,
        List<ProgressHighlight> highlights,
        Energy energy,
        Macros macros,
        Plate plate,
        Meals meals,
        Habits habits) {

    public ProgressFoodResponse {
        highlights = List.copyOf(highlights);
    }

    @Override
    public List<ProgressHighlight> highlights() {
        return List.copyOf(highlights);
    }

    public record Compared<T>(T value, T previous) {
    }

    public record Hero(Compared<Integer> eatenPerDay, Compared<BigDecimal> proteinPerDay, Balance balancePerDay,
                       DaysLogged daysLogged) {
    }

    /** The energy view's average balance of the current week and the days it counted. */
    public record Balance(Integer value, int daysLogged) {
    }

    /** days is the length of the period. */
    public record DaysLogged(int value, int previous, int days) {
    }

    public record Energy(AnalyticsEnergyWeeklyResponse thisWeek, List<WeekBalance> weeklyBalance) {

        public Energy {
            weeklyBalance = List.copyOf(weeklyBalance);
        }

        @Override
        public List<WeekBalance> weeklyBalance() {
            return List.copyOf(weeklyBalance);
        }
    }

    public record WeekBalance(LocalDate weekStart, Integer balance, int daysLogged, boolean current) {
    }

    public record Macros(Macro protein, Macro carbs, Macro fat, List<SummaryLine> summary) {

        public Macros {
            summary = List.copyOf(summary);
        }

        @Override
        public List<SummaryLine> summary() {
            return List.copyOf(summary);
        }
    }

    /** Grams a day over the logged days of each Monday week of the period. */
    public record Macro(BigDecimal firstWeek, BigDecimal thisWeek, BigDecimal target, List<WeekValue> series) {

        public Macro {
            series = List.copyOf(series);
        }

        @Override
        public List<WeekValue> series() {
            return List.copyOf(series);
        }
    }

    public record WeekValue(LocalDate weekStart, BigDecimal value) {
    }

    /** trend is UP, STEADY or DOWN. */
    public record SummaryLine(String text, String trend) {
    }

    /**
     * Shares of the plate by calories (analytics keeps no grams), drinks left out. Each list's shares add up to
     * exactly 100 when the list is not empty.
     */
    public record Plate(String basis, List<PlateGroup> current, List<PlateGroup> previous,
                        List<PlateChange> changes) {

        public Plate {
            current = List.copyOf(current);
            previous = List.copyOf(previous);
            changes = List.copyOf(changes);
        }

        @Override
        public List<PlateGroup> current() {
            return List.copyOf(current);
        }

        @Override
        public List<PlateGroup> previous() {
            return List.copyOf(previous);
        }

        @Override
        public List<PlateChange> changes() {
            return List.copyOf(changes);
        }
    }

    public record PlateGroup(String group, int kcal, BigDecimal share) {
    }

    public record PlateChange(String group, BigDecimal deltaPoints) {
    }

    public record Meals(List<MealShare> share) {

        public Meals {
            share = List.copyOf(share);
        }

        @Override
        public List<MealShare> share() {
            return List.copyOf(share);
        }
    }

    /** sharePct adds up to 100; deltaPoints is null when the previous period has no meals. */
    public record MealShare(String mealType, int sharePct, Integer deltaPoints, int kcalPerDay) {
    }

    /** Water and fibre live in nutrition-service and come from section 3.1. */
    public record Habits(int loggedDays) {
    }
}
