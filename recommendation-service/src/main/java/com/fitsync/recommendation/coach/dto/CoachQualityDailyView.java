package com.fitsync.recommendation.coach.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One day's answer counts. Present for every date in the range, even a day with none.")
public record CoachQualityDailyView(
        String date,
        int answers,
        int passed,
        int rejected,
        int refused,
        int modelUnavailable) {
}
