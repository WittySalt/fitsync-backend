package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fitsync.nutrition.domain.InsightPeriod;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "timezone": "Asia/Singapore",
          "period": "PREVIOUS_WEEK",
          "periodStart": "2026-09-21",
          "periodEnd": "2026-09-27",
          "daysLogged": 7,
          "proteinPerDayGrams": 118.4,
          "earlierProteinPerDayGrams": 121.0,
          "proteinTargetGrams": 140,
          "caloriesPerDay": 2290,
          "earlierCaloriesPerDay": 2310,
          "calorieTargetKcal": 2100,
          "proteinByMeal": [ { "mealType": "BREAKFAST", "label": "Breakfast", "gramsPerDay": 18.2 } ]
        }
        """)
public record NutritionPageInsightView(
        String timezone,
        InsightPeriod period,
        LocalDate periodStart,
        LocalDate periodEnd,
        int daysLogged,
        BigDecimal proteinPerDayGrams,
        BigDecimal earlierProteinPerDayGrams,
        BigDecimal proteinTargetGrams,
        Integer caloriesPerDay,
        Integer earlierCaloriesPerDay,
        Integer calorieTargetKcal,
        List<MealProteinShareView> proteinByMeal) {

    public NutritionPageInsightView {
        proteinByMeal = proteinByMeal == null ? List.of() : List.copyOf(proteinByMeal);
    }

    @Override
    public List<MealProteinShareView> proteinByMeal() {
        return List.copyOf(proteinByMeal);
    }
}
