package com.fitsync.recommendation.coach.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "How many insight-panel readings landed with a given surface, source and outcome.")
public record CoachQualityReadingCountView(String surface, String source, String outcome, int count) {
}
