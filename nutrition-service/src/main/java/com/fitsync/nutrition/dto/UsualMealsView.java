package com.fitsync.nutrition.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "timezone": "Asia/Singapore",
          "windowDays": 14,
          "meals": [
            {
              "mealType": "BREAKFAST",
              "label": "Breakfast",
              "usualTime": "07:30",
              "mealsInWindow": 13,
              "occurrences": 11,
              "sourceMealId": "1bee8206-5090-9fbc-4a06-8d2b397d4d5f",
              "totalCalories": 499,
              "totalProteinGrams": 30.3,
              "totalCarbsGrams": 62.0,
              "totalFatGrams": 12.5,
              "items": [ { "name": "Rolled Oats", "calories": 190, "proteinGrams": 6.5, "categoryCode": "GRAINS" } ]
            }
          ]
        }
        """)
public record UsualMealsView(String timezone, int windowDays, List<UsualMealView> meals) {

    public UsualMealsView {
        meals = meals == null ? List.of() : List.copyOf(meals);
    }

    @Override
    public List<UsualMealView> meals() {
        return List.copyOf(meals);
    }
}
