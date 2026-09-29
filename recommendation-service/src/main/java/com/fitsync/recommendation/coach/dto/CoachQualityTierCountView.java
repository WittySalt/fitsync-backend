package com.fitsync.recommendation.coach.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "How many assistant turns answered at a given tier.")
public record CoachQualityTierCountView(int tier, int count) {
}
