package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fitsync.nutrition.domain.MealType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Quick-add a calorie (+ optional macro) entry without picking a food — for eating-out / no-match
 * cases. Creates a meal with a single synthetic manual item. {@code mealType} defaults to SNACK and
 * {@code consumedAt} to now when omitted.
 */
@Schema(example = """
        {
          "mealType": "SNACK",
          "name": "Coffee with milk",
          "consumedAt": "2026-06-15T15:00:00Z",
          "calories": 120,
          "proteinGrams": 6.0,
          "carbsGrams": 12.0,
          "fatGrams": 5.0
        }
        """)
public record QuickAddMealRequest(
        MealType mealType,
        @Size(max = 120) String name,
        Instant consumedAt,
        @NotNull @Min(0) Integer calories,
        @DecimalMin(value = "0.0") BigDecimal proteinGrams,
        @DecimalMin(value = "0.0") BigDecimal carbsGrams,
        @DecimalMin(value = "0.0") BigDecimal fatGrams,
        @DecimalMin(value = "0.0") BigDecimal fiberGrams,
        @DecimalMin(value = "0.0") BigDecimal sugarGrams,
        @DecimalMin(value = "0.0") BigDecimal sodiumMg) {
}
