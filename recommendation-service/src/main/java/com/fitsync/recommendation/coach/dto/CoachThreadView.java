package com.fitsync.recommendation.coach.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A conversation with the coach, as it appears in the thread list.")
public record CoachThreadView(
        UUID threadId,
        String title,
        int turnCount,
        Instant lastActiveAt,
        Instant createdAt) {
}
