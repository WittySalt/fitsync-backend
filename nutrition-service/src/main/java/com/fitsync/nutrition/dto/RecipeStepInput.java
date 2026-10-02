package com.fitsync.nutrition.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** One step of a recipe's method. The step number is taken from the position in the list. */
public record RecipeStepInput(@NotBlank @Size(max = 1000) String text) {
}
