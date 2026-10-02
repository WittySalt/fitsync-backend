package com.fitsync.nutrition.dto;

import java.util.List;
import java.util.UUID;

/** One step of a recipe, with the cook mode fields worked out from its text when the detail is read. */
public record RecipeStepView(
        int stepNumber,
        String text,
        String title,
        List<UUID> ingredientIds,
        List<RecipeStepTimerView> timers) {

    public RecipeStepView {
        ingredientIds = ingredientIds == null ? List.of() : List.copyOf(ingredientIds);
        timers = timers == null ? List.of() : List.copyOf(timers);
    }

    @Override
    public List<UUID> ingredientIds() {
        return List.copyOf(ingredientIds);
    }

    @Override
    public List<RecipeStepTimerView> timers() {
        return List.copyOf(timers);
    }
}
