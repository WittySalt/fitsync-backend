package com.fitsync.recommendation.coach.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Call counts and latency for one tool, over every turn's use of it in the window.")
public record CoachQualityToolView(String tool, int calls, int failed, int medianMs) {
}
