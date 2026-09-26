package com.fitsync.recommendation.coach.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Why a draft was turned down.
 *
 * <p>Optional. A user who does not want to explain should still be able to say no; a reason when
 * one is given is the most direct signal the evaluation harness has about whether the coach is
 * proposing things worth doing.
 */
@Schema(description = "An optional reason for rejecting a draft.")
public record RejectSessionDraftRequest(
        @Size(max = 500, message = "Keep the reason under 500 characters")
        @Schema(example = "Too long for today, and I did shoulders on Tuesday")
        String reason) {
}
