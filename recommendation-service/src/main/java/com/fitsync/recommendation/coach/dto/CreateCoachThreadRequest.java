package com.fitsync.recommendation.coach.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * A title is optional on purpose. A user who has to name a conversation before having it will not
 * start one, so the title is derived from the opening question when none is given.
 */
@Schema(example = """
        {
          "title": "Planning this week"
        }
        """)
public record CreateCoachThreadRequest(@Size(max = 200) String title) {
}
