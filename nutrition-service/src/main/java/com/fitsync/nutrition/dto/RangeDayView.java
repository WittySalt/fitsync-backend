package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One day of a range summary. A day with no meals has logged false and null flags, and its amounts are zero.
 * Each flag compares the day's amount with the person's reference: Met for a target reached, Within for a limit
 * not passed.
 */
public record RangeDayView(
        LocalDate date,
        boolean logged,
        int mealCount,
        int totalCalories,
        Integer targetCalories,
        BigDecimal fiberGrams,
        BigDecimal potassiumMg,
        BigDecimal sugarGrams,
        BigDecimal saturatedFatGrams,
        BigDecimal sodiumMg,
        Boolean fiberMet,
        Boolean potassiumMet,
        Boolean sugarWithin,
        Boolean saturatedFatWithin,
        Boolean sodiumWithin) {
}
