package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.util.List;

/** The nutrition of one serving of a recipe, summed from its ingredients. */
public record RecipeServingNutritionView(
        Integer kcal,
        BigDecimal proteinGrams,
        BigDecimal carbsGrams,
        BigDecimal fatGrams,
        BigDecimal fiberGrams,
        BigDecimal sugarGrams,
        BigDecimal sodiumMg,
        BigDecimal saturatedFatGrams,
        BigDecimal potassiumMg,
        List<RecipeNutrientView> nutrients) {

    public RecipeServingNutritionView {
        nutrients = nutrients == null ? List.of() : List.copyOf(nutrients);
    }

    @Override
    public List<RecipeNutrientView> nutrients() {
        return List.copyOf(nutrients);
    }
}
