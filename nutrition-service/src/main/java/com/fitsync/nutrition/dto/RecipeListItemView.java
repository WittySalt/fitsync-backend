package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One recipe on a list page: what a card needs, with nutrition per serving. lastCookedAt is set only on the
 * "recent" scope, the time of the newest meal the caller logged from the recipe, and is null everywhere else.
 */
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
        BigDecimal fatGramsPerServing,
        List<String> tags,
        List<String> dietFlags,
        List<String> topFoodGroups,
        boolean favorite,
        Instant lastCookedAt) {

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
