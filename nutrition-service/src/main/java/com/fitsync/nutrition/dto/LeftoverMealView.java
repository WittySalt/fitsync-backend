package com.fitsync.nutrition.dto;

import java.util.UUID;

/** The meal a leftover was logged as and what is still left of that cook. */
public record LeftoverMealView(UUID cookId, int servingsLeft, MealView meal) {
}
