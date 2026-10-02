package com.fitsync.nutrition.dto;

/**
 * A duration written in a recipe step. {@code maxSeconds} equals {@code seconds} unless the step gives a range,
 * and {@code phrase} is the exact text of the step that holds the duration so the page can highlight it.
 */
public record RecipeStepTimerView(int seconds, int maxSeconds, boolean eachSide, String phrase) {
}
