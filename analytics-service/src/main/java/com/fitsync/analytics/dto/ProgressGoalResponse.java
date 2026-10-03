package com.fitsync.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The Goal page of Progress (contract section 2.1). Weight comes from the daily body rows, food from the
 * daily nutrition rows and sessions from the daily workout rows; every list is oldest first.
 */
public record ProgressGoalResponse(
        ProgressPeriod period,
        ProgressPeriod previous,
        Goal goal,
        Hero hero,
        List<ProgressHighlight> highlights,
        Weight weight,
        Body body,
        Pace pace,
        List<OtherGoal> otherGoals) {

    public ProgressGoalResponse {
        highlights = List.copyOf(highlights);
        otherGoals = List.copyOf(otherGoals);
    }

    @Override
    public List<ProgressHighlight> highlights() {
        return List.copyOf(highlights);
    }

    @Override
    public List<OtherGoal> otherGoals() {
        return List.copyOf(otherGoals);
    }

    /** The active weight goal; the whole object is null without one. */
    public record Goal(String type, BigDecimal targetKg, LocalDate byDate) {
    }

    /** A figure with the same figure for the previous period. */
    public record Compared<T>(T value, T previous) {
    }

    /**
     * nowKg is the newest weigh-in up to the end of the period, changeKg the last weekly average of the
     * period minus the last weekly average of the previous period, daysLeft the days from today to the goal
     * date (never below zero).
     */
    public record Hero(BigDecimal nowKg, LocalDate measuredOn, BigDecimal changeKg,
                       Compared<BigDecimal> weeklyRate, Compared<LocalDate> projectedDate, Integer daysLeft) {
    }

    public record Weight(List<DailyWeight> daily, List<WeeklyWeight> weekly, List<DailyWeight> projection,
                         RateChangeMark rateChangeMark, String method) {

        public Weight {
            daily = List.copyOf(daily);
            weekly = List.copyOf(weekly);
            projection = List.copyOf(projection);
        }

        @Override
        public List<DailyWeight> daily() {
            return List.copyOf(daily);
        }

        @Override
        public List<WeeklyWeight> weekly() {
            return List.copyOf(weekly);
        }

        @Override
        public List<DailyWeight> projection() {
            return List.copyOf(projection);
        }
    }

    public record DailyWeight(LocalDate date, BigDecimal kg) {
    }

    /** Only weeks with at least one weigh-in are listed. */
    public record WeeklyWeight(LocalDate weekStart, BigDecimal avgKg, int weighIns) {
    }

    /** The week whose 28-day rate moved most from the week before. */
    public record RateChangeMark(LocalDate date, String text, BigDecimal rateBefore, BigDecimal rateAfter) {
    }

    public record Body(Measure bodyFat, Measure waist, Measure bmi) {
    }

    /** start and end are the first and last readings of the period; context is null with fewer than two. */
    public record Measure(List<Reading> series, BigDecimal start, BigDecimal end, String context) {

        public Measure {
            series = List.copyOf(series);
        }

        @Override
        public List<Reading> series() {
            return List.copyOf(series);
        }
    }

    public record Reading(LocalDate date, BigDecimal value) {
    }

    /** The energy view's eight weeks up to the period's last week, cumulative from the first. */
    public record Pace(List<PaceWeek> weeks) {

        public Pace {
            weeks = List.copyOf(weeks);
        }

        @Override
        public List<PaceWeek> weeks() {
            return List.copyOf(weeks);
        }
    }

    public record PaceWeek(LocalDate weekStart, Double expectedKg, Double actualKg) {
    }

    /**
     * For CALORIES, PROTEIN, CARBS and FAT, metDays and loggedDays count days. For WORKOUT_FREQUENCY they count
     * Monday weeks (met weeks of the weeks begun so far), days marks the days with a session as MET, and weeks
     * holds the sessions of each week.
     */
    public record OtherGoal(String type, BigDecimal target, String unit, int metDays, int loggedDays,
                            List<DayState> days, List<WeekValue> weeks) {

        public OtherGoal {
            days = List.copyOf(days);
            weeks = List.copyOf(weeks);
        }

        @Override
        public List<DayState> days() {
            return List.copyOf(days);
        }

        @Override
        public List<WeekValue> weeks() {
            return List.copyOf(weeks);
        }
    }

    /** MET, MISSED, NOT_LOGGED or FUTURE. */
    public record DayState(LocalDate date, String state) {
    }

    public record WeekValue(LocalDate weekStart, BigDecimal value, BigDecimal target) {
    }
}
