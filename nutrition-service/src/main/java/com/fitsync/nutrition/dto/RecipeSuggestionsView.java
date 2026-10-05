package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * What to cook tonight: the state of the day, the facts the suggestions were measured against, and at most
 * three suggestions, each with one sentence that says why it was chosen.
 */
public record RecipeSuggestionsView(State state, Facts facts, List<Suggestion> suggestions) {

    /** How today stands, which decides how many suggestions there are and what they are for. */
    public enum State { FITS, NOTHING_LOGGED, PAST_TARGET, PROTEIN_ONLY, DINNER_LOGGED }

    /** Where a suggestion comes from. */
    public enum Source { LIBRARY, MINE, RECENT }

    /**
     * kcalLeft is the target minus what was eaten and can be negative, and proteinShortGrams is never below zero;
     * both are null without a target. lowestNutrient is null until something is logged today. usualDinnerTime is
     * the median dinner time of the last 30 days as HH:mm, or null when there were fewer than three dinners.
     * proteinMet is null without a protein target.
     */
    public record Facts(Integer kcalLeft, BigDecimal proteinShortGrams, RecipeFitView.LowestNutrient lowestNutrient,
                        String usualDinnerTime, Integer eatenKcal, Integer targetKcal, Boolean proteinMet) {
    }

    /** One recipe to cook, with nutrition for one serving. */
    public record Suggestion(UUID recipeId, String name, String imageUrl, Integer kcalPerServing,
                             BigDecimal proteinPerServing, String reason, Source source) {
    }

    public RecipeSuggestionsView {
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }

    @Override
    public List<Suggestion> suggestions() {
        return List.copyOf(suggestions);
    }
}
