package com.fitsync.recommendation.coach.reading;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * nutrition-service's own page-insight contract, read exactly as it defines it.
 *
 * <p>With {@link #PERIOD_NONE} every number is null and the meal list is empty, so a caller checks
 * the period before it looks at anything else. Unknown properties are ignored for the same reason
 * they are on {@link TrainingInsight}: the owning service can add fields without breaking a reading.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NutritionInsight(
        String timezone,
        String period,
        LocalDate periodStart,
        LocalDate periodEnd,
        Integer daysLogged,
        BigDecimal proteinPerDayGrams,
        BigDecimal earlierProteinPerDayGrams,
        BigDecimal proteinTargetGrams,
        BigDecimal caloriesPerDay,
        BigDecimal earlierCaloriesPerDay,
        BigDecimal calorieTargetKcal,
        List<MealProtein> proteinByMeal) {

    /** period value meaning "no full week was logged". */
    public static final String PERIOD_NONE = "NONE";

    public NutritionInsight {
        proteinByMeal = proteinByMeal == null ? List.of() : List.copyOf(proteinByMeal);
    }

    // Re-wraps in an immutable copy on every call, the same pattern TrainingInsight.lifts() uses,
    // because that is what satisfies findsecbugs' EI_EXPOSE_REP for a record component.
    @Override
    public List<MealProtein> proteinByMeal() {
        return List.copyOf(proteinByMeal);
    }

    /** True when the upstream reports that there is no week to read. */
    public boolean isNone() {
        return PERIOD_NONE.equals(period);
    }

    /** The protein eaten at one kind of meal, in grams a day over the period. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MealProtein(String mealType, String label, BigDecimal gramsPerDay) {
    }
}
