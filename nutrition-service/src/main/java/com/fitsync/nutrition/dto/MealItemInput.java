package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
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
        """)
public record MealItemInput(
        UUID customFoodId,
        UUID foodCatalogItemId,
        UUID foodServingId,
        @Size(max = 120) String externalFoodRef,
        @Size(max = 40) String barcode,
        @Size(max = 120) String foodName,
        @Size(max = 120) String brandName,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity,
        @Size(max = 30) String unit,
        @DecimalMin(value = "0.0") BigDecimal servingSize,
        @Size(max = 30) String servingUnit,
        @Size(max = 80) String snapshotServingLabel,
        @Min(0) Integer calories,
        @DecimalMin(value = "0.0") BigDecimal proteinGrams,
        @DecimalMin(value = "0.0") BigDecimal carbsGrams,
        @DecimalMin(value = "0.0") BigDecimal fatGrams,
        @DecimalMin(value = "0.0") BigDecimal fiberGrams,
        @DecimalMin(value = "0.0") BigDecimal sugarGrams,
        @DecimalMin(value = "0.0") BigDecimal sodiumMg,
        List<@Valid NutrientDetailInput> nutrientDetails) {

    public MealItemInput {
        nutrientDetails = nutrientDetails == null ? List.of() : List.copyOf(nutrientDetails);
    }

    @Override
    public List<NutrientDetailInput> nutrientDetails() {
        return List.copyOf(nutrientDetails);
    }
}
