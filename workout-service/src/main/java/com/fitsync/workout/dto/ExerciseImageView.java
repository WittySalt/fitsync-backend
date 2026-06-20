package com.fitsync.workout.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ExerciseImageView(
        @Schema(example = "0")
        Integer position,

        @Schema(example = "http://localhost:9000/fitsync-media/exercises/Barbell_Bench_Press/0.jpg")
        String url) {
}
