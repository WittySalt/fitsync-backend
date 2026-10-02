package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A whole recipe for its own page: ingredients, steps, nutrition per serving, tags, source and credit. */
public record RecipeDetailView(
        UUID id,
        String name,
        String scope,
        BigDecimal servings,
        String note,
        String category,
        String cuisine,
        Integer prepMinutes,
        String source,
        String sourceUrl,
        String imageUrl,
        String imageCredit,
        List<RecipeDetailIngredientView> ingredients,
        List<RecipeStepView> steps,
        RecipeServingNutritionView perServing,
        Map<String, BigDecimal> groupsGrams,
        List<String> tags,
        List<String> dietFlags,
        boolean favorite,
        boolean editable) {

    public RecipeDetailView {
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        steps = steps == null ? List.of() : List.copyOf(steps);
        groupsGrams = groupsGrams == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(groupsGrams));
        tags = tags == null ? List.of() : List.copyOf(tags);
        dietFlags = dietFlags == null ? List.of() : List.copyOf(dietFlags);
    }

    @Override
    public List<RecipeDetailIngredientView> ingredients() {
        return List.copyOf(ingredients);
    }

    @Override
    public List<RecipeStepView> steps() {
        return List.copyOf(steps);
    }

    @Override
    public Map<String, BigDecimal> groupsGrams() {
        return java.util.Collections.unmodifiableMap(groupsGrams);
    }

    @Override
    public List<String> tags() {
        return List.copyOf(tags);
    }

    @Override
    public List<String> dietFlags() {
        return List.copyOf(dietFlags);
    }
}
