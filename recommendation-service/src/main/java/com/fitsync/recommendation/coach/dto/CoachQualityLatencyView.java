package com.fitsync.recommendation.coach.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Latency over passed answers only: nearest-rank median and 95th percentile, in milliseconds.")
public record CoachQualityLatencyView(int count, int medianMs, int p95Ms) {
}
