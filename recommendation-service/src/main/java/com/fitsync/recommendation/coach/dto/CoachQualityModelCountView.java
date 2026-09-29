package com.fitsync.recommendation.coach.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "How many assistant turns a given model answered.")
public record CoachQualityModelCountView(String model, int count) {
}
