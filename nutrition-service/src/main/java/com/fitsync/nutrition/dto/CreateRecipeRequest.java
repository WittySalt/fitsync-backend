package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "name": "High-Protein Chicken Bowl",
          "servings": 2,
          "note": "Meal-prep lunch.",
          "ingredients": [
            { "foodCatalogItemId": "f00d0001-0000-0000-0000-000000000000", "quantity": 2.0 },
            { "foodCatalogItemId": "f00d0015-0000-0000-0000-000000000000", "quantity": 1.5 }
          ]
        }
        """)
public record CreateRecipeRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal servings,
        @Size(max = 300) String note,
        @NotEmpty List<@Valid RecipeIngredientInput> ingredients) {

    public CreateRecipeRequest {
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
    }

    @Override
    public List<RecipeIngredientInput> ingredients() {
        return List.copyOf(ingredients);
    }
}
