package com.fitsync.nutrition.dto;

import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.domain.MealType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Create a saved-meal template. Provide either {@code fromMealId} (snapshot an existing meal's
 * items) or an inline {@code items} list — exactly one.
 */
@Schema(example = """
        {
          "name": "Standard breakfast",
          "mealType": "BREAKFAST",
          "items": [
            { "foodCatalogItemId": "f00d0005-0000-0000-0000-000000000000", "foodServingId": null, "quantity": 2.0 }
          ]
        }
        """)
public record CreateMealTemplateRequest(
        @NotBlank @Size(max = 120) String name,
        MealType mealType,
        @Size(max = 300) String note,
        UUID fromMealId,
        List<@Valid MealItemInput> items) {

    public CreateMealTemplateRequest {
        items = items == null ? List.of() : List.copyOf(items);
    }

    @Override
    public List<MealItemInput> items() {
        return List.copyOf(items);
    }
}
