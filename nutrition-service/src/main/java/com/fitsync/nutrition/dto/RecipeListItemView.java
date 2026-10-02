package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** One recipe on a list page: what a card needs, with nutrition per serving. */
public record RecipeListItemView(
        UUID id,
        String name,
        String scope,
        String imageUrl,
        String imageCredit,
        String category,
        String cuisine,
        BigDecimal servings,
        Integer kcalPerServing,
        BigDecimal proteinGramsPerServing,
        List<String> tags,
        List<String> dietFlags,
        List<String> topFoodGroups,
        boolean favorite) {

    public RecipeListItemView {
        tags = tags == null ? List.of() : List.copyOf(tags);
        dietFlags = dietFlags == null ? List.of() : List.copyOf(dietFlags);
        topFoodGroups = topFoodGroups == null ? List.of() : List.copyOf(topFoodGroups);
    }

    @Override
    public List<String> tags() {
        return List.copyOf(tags);
    }

    @Override
    public List<String> dietFlags() {
        return List.copyOf(dietFlags);
    }

    @Override
    public List<String> topFoodGroups() {
        return List.copyOf(topFoodGroups);
    }
}
