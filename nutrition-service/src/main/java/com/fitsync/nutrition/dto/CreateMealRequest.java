package com.fitsync.nutrition.dto;

import java.time.Instant;
import java.util.List;

import com.fitsync.nutrition.domain.MealSource;
import com.fitsync.nutrition.domain.MealType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "mealType": "LUNCH",
          "name": "Chicken rice lunch",
          "source": "MANUAL",
          "consumedAt": "2026-06-12T04:30:00Z",
          "timezone": "Asia/Singapore",
          "note": "Lunch after workout.",
          "items": [
            {
              "customFoodId": null,
              "foodCatalogItemId": "33333333-3333-3333-3333-333333333333",
              "foodServingId": "44444444-4444-4444-4444-444444444444",
              "externalFoodRef": null,
              "barcode": "8888000012345",
              "foodName": "Chicken Rice",
              "brandName": "FitSync Kitchen",
              "quantity": 1.0,
              "unit": "serving",
              "servingSize": 350.0,
              "servingUnit": "g",
              "snapshotServingLabel": "1 plate",
              "calories": 620,
              "proteinGrams": 32.0,
              "carbsGrams": 72.0,
              "fatGrams": 22.0,
              "fiberGrams": 3.5,
              "sugarGrams": 4.0,
              "sodiumMg": 980.0,
              "nutrientDetails": [
                {
                  "nutrientCode": "SAT_FAT",
                  "displayName": "Saturated fat",
                  "amount": 6.5,
                  "unit": "g",
                  "sortOrder": 10
                }
              ]
            }
          ]
        }
        """)
public record CreateMealRequest(
        @NotNull MealType mealType,
        @Size(max = 120) String name,
        @NotNull MealSource source,
        @NotNull Instant consumedAt,
        @Size(max = 80) String timezone,
        @Size(max = 300) String note,
        @NotEmpty List<@Valid MealItemInput> items) {

    public CreateMealRequest {
        items = items == null ? List.of() : List.copyOf(items);
    }

    @Override
    public List<MealItemInput> items() {
        return List.copyOf(items);
    }
}
