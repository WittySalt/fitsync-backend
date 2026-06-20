package com.fitsync.workout.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ExerciseInstructionView(
        @Schema(example = "1")
        Integer stepNumber,

        @Schema(example = "Keep your chest tall and brace your core.")
        String text) {
}
