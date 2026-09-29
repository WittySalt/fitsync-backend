package com.fitsync.recommendation.coach.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "How assistant answers in the window were classified by the guardrail.")
public record CoachQualityAnswersView(
        int total,
        int passed,
        int rejected,
        int refused,
        int modelUnavailable,
        int other,
        List<CoachQualityRuleCountView> rejectedByRule,
        List<CoachQualityRuleCountView> refusedByRule) {

    public CoachQualityAnswersView {
        rejectedByRule = rejectedByRule == null ? List.of() : List.copyOf(rejectedByRule);
        refusedByRule = refusedByRule == null ? List.of() : List.copyOf(refusedByRule);
    }

    @Override
    public List<CoachQualityRuleCountView> rejectedByRule() {
        return List.copyOf(rejectedByRule);
    }

    @Override
    public List<CoachQualityRuleCountView> refusedByRule() {
        return List.copyOf(refusedByRule);
    }
}
