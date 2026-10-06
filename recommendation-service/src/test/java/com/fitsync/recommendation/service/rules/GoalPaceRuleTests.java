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
    void reportsAheadWhenTheProjectedDateIsAWeekOrMoreEarly() {
        RuleOutcome outcome = rule.evaluate(context("87.20")).orElseThrow();

        // actual = (87.2-90)/28*7 = -0.70 kg/week; 7.2 kg left takes 72 days, 16 Sept, 15 days early.
        assertThat(outcome.facts())
                .containsEntry("status", "AHEAD")
                .containsEntry("requiredRatePerWeekKg", new BigDecimal("-0.61"))
                .containsEntry("actualRatePerWeekKg", new BigDecimal("-0.70"))
                .containsEntry("distanceKg", new BigDecimal("7.2"))
                .containsEntry("projectedDate", LocalDate.parse("2026-09-16"))
                .containsEntry("daysEarly", 15L)
                .containsEntry("daysRemaining", 87L);
    }

    @Test
    void reportsOnCourseWithinAWeekOfTheGoalDate() {
        // actual = -0.60 kg/week; 7.6 kg left lands on 3 Oct, two days after the goal date.
        assertThat(rule.evaluate(context("87.60")).orElseThrow().facts())
                .containsEntry("status", "ON_TRACK")
                .containsEntry("projectedDate", LocalDate.parse("2026-10-03"));
    }

    @Test
    void reportsBehindWhenTheProjectedDateIsAWeekOrMoreLate() {
        // actual = -0.50 kg/week; 8 kg left lands on 26 Oct, 25 days late.
        assertThat(rule.evaluate(context("88.00")).orElseThrow().facts())
                .containsEntry("status", "BEHIND")
                .containsEntry("daysLate", 25L);
    }

    @Test
    void movingOppositeToTheGoalDirectionIsAlwaysBehind() {
        assertThat(rule.evaluate(context("91.00")).orElseThrow().facts())
                .containsEntry("status", "BEHIND")
                .doesNotContainKey("projectedDate");
    }

    @Test
    void neverSaysBehindWhereTheProgressRoadSaysAhead() {
        // Like the steady account: 74.2 kg at the start, 71.2 kg now, 68 kg by 11 December.
        RecommendationContextEntity steady = context("71.20");
        steady.setGoalBaselineWeightKg(new BigDecimal("74.20"));
        steady.setGoalBaselineOn(LocalDate.parse("2026-06-08"));
        steady.setCurrentGoalTargetValue(new BigDecimal("68"));
        steady.setCurrentGoalTargetDate(LocalDate.parse("2026-12-11"));

        RuleOutcome outcome = rule.evaluate(steady).orElseThrow();

        assertThat(outcome.facts()).containsEntry("status", "AHEAD").containsEntry("distanceKg", new BigDecimal("3.2"));
        assertThat(new NarrativeTemplates().render(outcome)).startsWith("3.2 kg to 68 kg. On course for ")
                .contains("days early").doesNotContain("behind");
    }

    @Test
    void aWeightAtOrPastTheTargetIsReached() {
        assertThat(rule.evaluate(context("79.80")).orElseThrow().facts())
                .containsEntry("status", "REACHED");
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
