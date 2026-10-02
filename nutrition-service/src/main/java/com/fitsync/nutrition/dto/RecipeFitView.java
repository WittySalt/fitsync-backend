package com.fitsync.nutrition.dto;

import java.math.BigDecimal;

/**
 * What the "fits today" ranking was measured against, so the page can explain the order. kcalLeft is the
 * calorie target minus what was eaten today and can be negative, or null when the person has no target.
 * proteinShortGrams is the protein still missing to the target, never below zero, or null without a target.
 * lowestNutrient is the vitamin or mineral furthest below its daily reference so far today, or null when
 * nothing has been logged today.
 */
public record RecipeFitView(Integer kcalLeft, BigDecimal proteinShortGrams, LowestNutrient lowestNutrient) {

    /** The nutrient of the day that is furthest below its reference. */
    public record LowestNutrient(String code, String displayName) {
    }
}
