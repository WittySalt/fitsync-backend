package com.fitsync.recommendation.service.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.junit.jupiter.api.Test;

class WeeklyAdherenceRuleTests {

    private final WeeklyAdherenceRule rule = new WeeklyAdherenceRule();

    @Test
    void reportsShortfallAgainstTheWeeklyTarget() {
        RecommendationContextEntity context = context(4, 2);
        context.setRecentWorkoutCount(3);
        context.setRecentWeekStart(LocalDate.parse("2026-06-29"));

        RuleOutcome outcome = rule.evaluate(context).orElseThrow();

        assertThat(outcome.facts())
                .containsEntry("targetDaysPerWeek", 4)
                .containsEntry("targetBasis", WeeklyAdherenceRule.TARGET_BASIS_PREFERENCE)
                .containsEntry("activeDays", 2)
                .containsEntry("shortfallDays", 2)
                .containsEntry("status", "MISSED")
                .containsEntry("workoutCount", 3)
                .containsEntry("weekStart", LocalDate.parse("2026-06-29"));
    }

    @Test
    void reportsMetWhenTheTargetIsReachedOrExceeded() {
        RuleOutcome outcome = rule.evaluate(context(3, 5)).orElseThrow();

        assertThat(outcome.facts())
                .containsEntry("shortfallDays", 0)
                .containsEntry("status", "MET");
    }

    @Test
    void staysQuietWithoutATargetOrARecap() {
        assertThat(rule.evaluate(context(null, 3))).isEmpty();
        assertThat(rule.evaluate(context(0, 3))).isEmpty();
        assertThat(rule.evaluate(context(4, null))).isEmpty();
    }

    @Test
    void prefersAnExplicitWorkoutFrequencyGoalOverThePreference() {
        RecommendationContextEntity context = context(5, 4);
        context.setWeeklyWorkoutGoalDays(4);

        RuleOutcome outcome = rule.evaluate(context).orElseThrow();

        assertThat(outcome.facts())
                .containsEntry("targetDaysPerWeek", 4)
                .containsEntry("targetBasis", WeeklyAdherenceRule.TARGET_BASIS_GOAL)
                .containsEntry("shortfallDays", 0)
                .containsEntry("status", "MET");
    }

    @Test
    void fallsBackToThePreferenceWhenTheGoalIsZeroOrAbsent() {
        RecommendationContextEntity zeroGoal = context(5, 4);
        zeroGoal.setWeeklyWorkoutGoalDays(0);
        RuleOutcome zeroGoalOutcome = rule.evaluate(zeroGoal).orElseThrow();
        assertThat(zeroGoalOutcome.facts())
                .containsEntry("targetDaysPerWeek", 5)
                .containsEntry("targetBasis", WeeklyAdherenceRule.TARGET_BASIS_PREFERENCE)
                .containsEntry("shortfallDays", 1);

        RecommendationContextEntity noGoal = context(5, 4);
        noGoal.setWeeklyWorkoutGoalDays(null);
        RuleOutcome noGoalOutcome = rule.evaluate(noGoal).orElseThrow();
        assertThat(noGoalOutcome.facts())
                .containsEntry("targetDaysPerWeek", 5)
                .containsEntry("targetBasis", WeeklyAdherenceRule.TARGET_BASIS_PREFERENCE);
    }

    private static RecommendationContextEntity context(Integer targetDays, Integer activeDays) {
        RecommendationContextEntity context = new RecommendationContextEntity();
        context.setUserId(UUID.randomUUID());
        context.setWorkoutDaysPerWeek(targetDays);
        context.setRecentActiveDays(activeDays);
        return context;
    }
}
