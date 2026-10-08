package com.fitsync.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;

/**
 * The adherence ratios stored on a daily nutrition row: calories consumed over the calorie target and
 * protein eaten over the protein target, to four decimals. The meal projection and the nutrition rebuild
 * both write them, so they share this to keep a rebuilt day identical to one built from events.
 */
public final class NutritionAdherence {

    private NutritionAdherence() {
    }

    /**
     * Recomputes both ratios from the row's totals and targets. A ratio whose target is missing or zero is
     * left as it was, as both callers always did; nothing is divided by zero.
     */
    public static void recalculate(AnalyticsUserDailyNutritionEntity daily) {
        if (daily.getCalorieTarget() != null && daily.getCalorieTarget() > 0) {
            int consumed = daily.getTotalCaloriesConsumed() == null ? 0 : daily.getTotalCaloriesConsumed();
            daily.setCalorieAdherenceRatio(
                    BigDecimal.valueOf(consumed)
                            .divide(BigDecimal.valueOf(daily.getCalorieTarget()), 4, RoundingMode.HALF_UP));
        }
        if (daily.getProteinTargetGrams() != null && daily.getProteinTargetGrams().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal protein = daily.getTotalProteinGrams() == null ? BigDecimal.ZERO : daily.getTotalProteinGrams();
            daily.setMacroAdherenceScore(
                    protein.divide(daily.getProteinTargetGrams(), 4, RoundingMode.HALF_UP));
        }
    }
}
