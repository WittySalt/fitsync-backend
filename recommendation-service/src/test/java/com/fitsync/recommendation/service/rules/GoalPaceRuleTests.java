package com.fitsync.recommendation.service.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.junit.jupiter.api.Test;

class GoalPaceRuleTests {

    // Fixed "today": 2026-07-06. Baseline 90 kg on 2026-06-08 (28 days elapsed),
    // target 80 kg by 2026-10-01 (115 days total) → required ≈ -0.61 kg/week.
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-06T12:00:00Z"), ZoneOffset.UTC);
    private final GoalPaceRule rule = new GoalPaceRule(clock);

    @Test
    void reportsAheadWhenLosingFasterThanRequired() {
        RuleOutcome outcome = rule.evaluate(context("87.20")).orElseThrow();

        // actual = (87.2-90)/28*7 = -0.70 kg/week vs required -0.61 → ratio 1.15.
        assertThat(outcome.facts())
                .containsEntry("status", "AHEAD")
                .containsEntry("requiredRatePerWeekKg", new BigDecimal("-0.61"))
                .containsEntry("actualRatePerWeekKg", new BigDecimal("-0.70"))
                .containsEntry("daysRemaining", 87L);
    }

    @Test
    void reportsOnTrackWithinTheTolerance() {
        // actual = (87.6-90)/28*7 = -0.60 → ratio ≈ 0.99.
        assertThat(rule.evaluate(context("87.60")).orElseThrow().facts())
                .containsEntry("status", "ON_TRACK");
    }

    @Test
    void reportsBehindWhenLosingTooSlowly() {
        // actual = (88-90)/28*7 = -0.50 → ratio ≈ 0.82.
        assertThat(rule.evaluate(context("88.00")).orElseThrow().facts())
                .containsEntry("status", "BEHIND");
    }

    @Test
    void movingOppositeToTheGoalDirectionIsAlwaysBehind() {
        assertThat(rule.evaluate(context("91.00")).orElseThrow().facts())
                .containsEntry("status", "BEHIND");
    }

    @Test
    void staysQuietWithoutBaselineTargetDateOrFreshSignal() {
        RecommendationContextEntity missingBaseline = context("87.20");
        missingBaseline.setGoalBaselineWeightKg(null);
        assertThat(rule.evaluate(missingBaseline)).isEmpty();

        RecommendationContextEntity expired = context("87.20");
        expired.setCurrentGoalTargetDate(LocalDate.parse("2026-07-01"));
        assertThat(rule.evaluate(expired)).isEmpty();

        RecommendationContextEntity baselineToday = context("87.20");
        baselineToday.setGoalBaselineOn(LocalDate.parse("2026-07-06"));
        assertThat(rule.evaluate(baselineToday)).isEmpty();

        RecommendationContextEntity notAWeightGoal = context("87.20");
        notAWeightGoal.setCurrentGoalType("CALORIES");
        assertThat(rule.evaluate(notAWeightGoal)).isEmpty();
    }

    private static RecommendationContextEntity context(String latestWeightKg) {
        RecommendationContextEntity context = new RecommendationContextEntity();
        context.setUserId(UUID.randomUUID());
        context.setCurrentGoalType("WEIGHT");
        context.setCurrentGoalTargetValue(new BigDecimal("80"));
        context.setCurrentGoalTargetDate(LocalDate.parse("2026-10-01"));
        context.setGoalBaselineWeightKg(new BigDecimal("90.00"));
        context.setGoalBaselineOn(LocalDate.parse("2026-06-08"));
        context.setLatestWeightKg(new BigDecimal(latestWeightKg));
        return context;
    }
}
