package com.fitsync.recommendation.coach.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * What the user wants a session for.
 *
 * <p>Optional, and that is the point. The coach knows what they trained yesterday, what equipment
 * they have and how long they usually train; asking for a session with no further instruction is
 * the normal case, and having to describe one would defeat the feature.
 */
@Schema(description = "An optional request describing the session wanted.")
public record DraftSessionRequest(
        @Size(max = 500, message = "Keep the request under 500 characters")
        @Schema(example = "Something for chest and back, I have a barbell")
        String request) {
}
