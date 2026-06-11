package com.fitsync.nutrition.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "name": "High Protein Overnight Oats",
          "brandName": "Home Recipe",
          "servingSize": 250.0,
          "servingUnit": "g",
          "calories": 410,
          "proteinGrams": 32.0,
          "carbsGrams": 48.0,
          "fatGrams": 9.0,
          "fiberGrams": 8.0,
          "sugarGrams": 12.0,
          "sodiumMg": 180.0
        }
        """)
public record CreateCustomFoodRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 120) String brandName,
        @NotNull @DecimalMin(value = "0.0") BigDecimal servingSize,
        @NotBlank @Size(max = 30) String servingUnit,
        @NotNull @Min(0) Integer calories,
        @DecimalMin(value = "0.0") BigDecimal proteinGrams,
        @DecimalMin(value = "0.0") BigDecimal carbsGrams,
        @DecimalMin(value = "0.0") BigDecimal fatGrams,
        @DecimalMin(value = "0.0") BigDecimal fiberGrams,
        @DecimalMin(value = "0.0") BigDecimal sugarGrams,
        @DecimalMin(value = "0.0") BigDecimal sodiumMg) {
}
