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

class NutritionGapRuleTests {

    // Noon UTC on 2026-07-06.
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-06T12:00:00Z"), ZoneOffset.UTC);
    private final NutritionGapRule rule = new NutritionGapRule(clock);

    @Test
    void estimatesTdeeTargetWithWeightLossDeficitAndSubtractsTodayIntake() {
        RecommendationContextEntity context = baseContext();
        context.setHeightCm(new BigDecimal("181.5"));
        context.setLatestWeightKg(new BigDecimal("82.50"));
        context.setActivityLevel("MODERATE");
        context.setCurrentGoalType("WEIGHT");
        context.setCurrentGoalTargetValue(new BigDecimal("75"));
        context.setTodayDate(LocalDate.parse("2026-07-06"));
        context.setTodayCaloriesConsumed(1050);
        context.setTodayProteinGrams(new BigDecimal("65.50"));

        RuleOutcome outcome = rule.evaluate(context).orElseThrow();

        // BMR = 10*82.5 + 6.25*181.5 - 5*30 - 78 = 1731.375; TDEE = *1.55 = 2684; -500 deficit.
        assertThat(outcome.facts())
                .containsEntry("basis", NutritionGapRule.BASIS_TDEE_ESTIMATE)
                .containsEntry("targetCalories", 2184)
                .containsEntry("consumedCalories", 1050)
                .containsEntry("remainingCalories", 1134)
                .containsEntry("proteinTargetGrams", new BigDecimal("132.0"))
                .containsEntry("proteinRemainingGrams", new BigDecimal("66.50"));
    }

    @Test
    void ignoresRunningTotalsFromAnEarlierDay() {
        RecommendationContextEntity context = baseContext();
        context.setHeightCm(new BigDecimal("181.5"));
        context.setLatestWeightKg(new BigDecimal("82.50"));
        context.setActivityLevel("MODERATE");
        context.setTodayDate(LocalDate.parse("2026-07-05"));
        context.setTodayCaloriesConsumed(1800);

        RuleOutcome outcome = rule.evaluate(context).orElseThrow();

        assertThat(outcome.facts())
                .containsEntry("consumedCalories", 0)
                .containsEntry("targetCalories", 2684)
                .containsEntry("remainingCalories", 2684);
    }

    @Test
    void prefersThePreciseEnergyProfileOverTheLocalEstimate() {
        RecommendationContextEntity context = baseContext();
        context.setHeightCm(new BigDecimal("181.5"));
        context.setLatestWeightKg(new BigDecimal("82.50"));
        context.setActivityLevel("MODERATE");
        context.setCurrentGoalType("WEIGHT");
        context.setCurrentGoalTargetValue(new BigDecimal("75"));
        // EV-3: user-service computed the exact TDEE (real age/sex) = 2812.
        context.setEnergyTargetCalories(2812);

        RuleOutcome outcome = rule.evaluate(context).orElseThrow();

        // 2812 - 500 weight-loss deficit = 2312, replacing the 2184 neutral estimate.
        assertThat(outcome.facts())
                .containsEntry("basis", NutritionGapRule.BASIS_ENERGY_PROFILE)
                .containsEntry("targetCalories", 2312);
    }

    @Test
    void prefersAnExplicitCalorieGoalOverTheEstimate() {
        RecommendationContextEntity context = baseContext();
        context.setCurrentGoalType("CALORIES");
        context.setCurrentGoalTargetValue(new BigDecimal("2100"));

        RuleOutcome outcome = rule.evaluate(context).orElseThrow();

        assertThat(outcome.facts())
                .containsEntry("basis", "CALORIE_GOAL")
                .containsEntry("targetCalories", 2100)
                .doesNotContainKey("proteinTargetGrams");
    }

    @Test
    void staysQuietWithoutACalorieGoalOrTdeeInputs() {
        RecommendationContextEntity context = baseContext();
        context.setLatestWeightKg(new BigDecimal("82.50"));

        assertThat(rule.evaluate(context)).isEmpty();
    }

    private static RecommendationContextEntity baseContext() {
        RecommendationContextEntity context = new RecommendationContextEntity();
        context.setUserId(UUID.randomUUID());
        return context;
    }
}
