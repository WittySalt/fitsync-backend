package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One day of a range summary. A day with no meals has logged false and null amounts and flags. On a logged day, an amount that no meal carries a value for is
 * null together with its flag, because unknown is not zero.
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
