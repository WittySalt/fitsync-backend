package com.fitsync.nutrition.dto;

/** How much of the logged food carries vitamin and mineral data, so the page can say what the averages rest on. */
public record NutrientCoverageView(long itemsLogged, long itemsWithoutNutrients) {
}
