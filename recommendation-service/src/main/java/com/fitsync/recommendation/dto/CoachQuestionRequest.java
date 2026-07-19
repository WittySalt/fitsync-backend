package com.fitsync.recommendation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(example = """
        {
          "question": "How am I doing against my weight goal this week?"
        }
        """)
public record CoachQuestionRequest(
        @NotBlank @Size(max = 500) String question) {
}
