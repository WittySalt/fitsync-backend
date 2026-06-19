package com.fitsync.nutrition.dto;

import java.time.Instant;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

/** Log a hydration entry. {@code loggedAt} defaults to now when omitted. */
@Schema(example = """
        { "amountMl": 250, "loggedAt": "2026-06-16T09:30:00Z", "timezone": "Asia/Singapore" }
        """)
public record LogWaterRequest(
        @NotNull @Min(1) Integer amountMl,
        Instant loggedAt,
        @Size(max = 80) String timezone) {
}
