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
          "nutrientCode": "SAT_FAT",
          "displayName": "Saturated fat",
          "amount": 6.5,
          "unit": "g",
          "sortOrder": 10
        }
        """)
public record NutrientDetailInput(
        @NotBlank @Size(max = 40) String nutrientCode,
        @NotBlank @Size(max = 80) String displayName,
        @NotNull @DecimalMin(value = "0.0") BigDecimal amount,
        @NotBlank @Size(max = 20) String unit,
        @NotNull @Min(0) Integer sortOrder) {
}
