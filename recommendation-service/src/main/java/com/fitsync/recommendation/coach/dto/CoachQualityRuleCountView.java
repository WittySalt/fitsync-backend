package com.fitsync.recommendation.coach.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "How many answers a single guardrail rule accounts for.")
public record CoachQualityRuleCountView(String rule, int count) {
}
