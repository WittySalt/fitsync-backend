package com.fitsync.recommendation.coach.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(example = """
        {
          "message": "Should I train today?"
        }
        """)
public record SendCoachMessageRequest(
        @NotBlank @Size(max = 1000) String message,
        @Valid CoachMessageContext context) {
}
