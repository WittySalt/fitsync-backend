package com.fitsync.recommendation.coach.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(example = """
        {
          "message": "Should I train today?"
        }
        """)
public record SendCoachMessageRequest(
        @NotBlank @Size(max = 1000) String message,
        @Valid CoachMessageContext context,
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        @Schema(description = "Optional id the client chose for this question. Sending the same id again "
                + "returns the first attempt's answer instead of asking twice. The Idempotency-Key "
                + "header does the same and wins when both are sent.")
        String clientTurnId) {

    /** A request without a client turn id, which is what callers that predate it send. */
    public SendCoachMessageRequest(String message, CoachMessageContext context) {
        this(message, context, null);
    }
}
