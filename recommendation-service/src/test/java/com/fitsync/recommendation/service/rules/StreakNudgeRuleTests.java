package com.fitsync.recommendation.service.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.junit.jupiter.api.Test;

class StreakNudgeRuleTests {

    private final StreakNudgeRule rule = new StreakNudgeRule();

    @Test
    void pointsAtTheNextMilestoneOnTheLadder() {
        RuleOutcome outcome = rule.evaluate(context(7)).orElseThrow();

        assertThat(outcome.facts())
                .containsEntry("streakDays", 7)
                .containsEntry("nextMilestone", 14)
                .containsEntry("daysToNextMilestone", 7);
    }

    @Test
    void beyondTheLadderThereIsNoNextMilestone() {
        RuleOutcome outcome = rule.evaluate(context(400)).orElseThrow();

        assertThat(outcome.facts())
                .containsEntry("streakDays", 400)
                .doesNotContainKey("nextMilestone");
    }

    @Test
    void staysQuietWithoutAStreak() {
        assertThat(rule.evaluate(context(null))).isEmpty();
        assertThat(rule.evaluate(context(0))).isEmpty();
    }

    private static RecommendationContextEntity context(Integer streakDays) {
        RecommendationContextEntity context = new RecommendationContextEntity();
        context.setUserId(UUID.randomUUID());
        context.setCurrentStreakDays(streakDays);
        return context;
    }
}
