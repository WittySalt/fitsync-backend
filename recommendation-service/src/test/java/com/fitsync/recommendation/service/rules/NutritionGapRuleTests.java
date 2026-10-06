package com.fitsync.recommendation.service.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.fitsync.recommendation.domain.TodayIntake;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.junit.jupiter.api.Test;

class NutritionGapRuleTests {

    private static final LocalDate TODAY = LocalDate.parse("2026-07-06");
    private final NutritionGapRule rule = new NutritionGapRule();

    @Test
    void estimatesTdeeTargetWithWeightLossDeficitAndSubtractsTodayIntake() {
        RecommendationContextEntity context = baseContext();
        context.setHeightCm(new BigDecimal("181.5"));
        context.setLatestWeightKg(new BigDecimal("82.50"));
        context.setActivityLevel("MODERATE");
        context.setCurrentGoalType("WEIGHT");
        context.setCurrentGoalTargetValue(new BigDecimal("75"));

        RuleOutcome outcome = rule.evaluate(context, new TodayIntake(TODAY, 1050, new BigDecimal("65.50")))
                .orElseThrow();

        // BMR = 10*82.5 + 6.25*181.5 - 5*30 - 78 = 1731.375; TDEE = *1.55 = 2684; -500 deficit.
        assertThat(outcome.facts())
                .containsEntry("basis", NutritionGapRule.BASIS_TDEE_ESTIMATE)
                .containsEntry("targetCalories", 2184)
                .containsEntry("consumedCalories", 1050)
                .containsEntry("remainingCalories", 1134)
                .containsEntry("proteinBasis", NutritionGapRule.BASIS_BODY_WEIGHT)
                .containsEntry("proteinTargetGrams", new BigDecimal("132.0"))
                .containsEntry("proteinRemainingGrams", new BigDecimal("66.50"));
    }

    @Test
    void prefersAnExplicitProteinGoalOverTheBodyWeightEstimate() {
        RecommendationContextEntity context = baseContext();
        context.setLatestWeightKg(new BigDecimal("82.50"));
        context.setProteinTargetGrams(new BigDecimal("140"));
        context.setCalorieGoalKcal(2100);

        RuleOutcome outcome = rule.evaluate(context, new TodayIntake(TODAY, 0, new BigDecimal("65.50")))
                .orElseThrow();

        assertThat(outcome.facts())
                .containsEntry("proteinBasis", NutritionGapRule.BASIS_PROTEIN_GOAL)
                .containsEntry("proteinTargetGrams", new BigDecimal("140.0"))
                .containsEntry("proteinRemainingGrams", new BigDecimal("74.50"));
    }

    @Test
    void showsProteinFactsFromAGoalEvenWithoutAKnownWeight() {
        RecommendationContextEntity context = baseContext();
        context.setProteinTargetGrams(new BigDecimal("120"));
        context.setCalorieGoalKcal(2100);

        RuleOutcome outcome = rule.evaluate(context).orElseThrow();

        assertThat(outcome.facts())
                .containsEntry("proteinBasis", NutritionGapRule.BASIS_PROTEIN_GOAL)
                .containsEntry("proteinTargetGrams", new BigDecimal("120.0"));
    }

    @Test
    void statesTheTargetsOnlyWhenTheDiaryCouldNotBeRead() {
        RecommendationContextEntity context = baseContext();
        context.setHeightCm(new BigDecimal("181.5"));
        context.setLatestWeightKg(new BigDecimal("82.50"));
        context.setActivityLevel("MODERATE");
        // A stale running total left by an older build is never quoted.
        context.setTodayDate(TODAY);
        context.setTodayCaloriesConsumed(4910);

        RuleOutcome outcome = rule.evaluate(context, TodayIntake.unknown()).orElseThrow();

        assertThat(outcome.facts())
                .containsEntry("targetCalories", 2684)
                .doesNotContainKeys("consumedCalories", "remainingCalories", "proteinConsumedGrams",
                        "proteinRemainingGrams")
                .containsKey("proteinTargetGrams");
    }

    @Test
    void quotesTheDiaryFigureAfterAMealWasLoggedAndDeleted() {
        RecommendationContextEntity context = baseContext();
        context.setCalorieGoalKcal(2100);
        context.setProteinTargetGrams(new BigDecimal("140"));
        context.setTodayDate(TODAY);
        context.setTodayCaloriesConsumed(4910);

        // The diary holds 476 kcal once the deleted meal is gone.
        RuleOutcome outcome = rule.evaluate(context, new TodayIntake(TODAY, 476, new BigDecimal("31.2")))
                .orElseThrow();

        assertThat(outcome.facts())
                .containsEntry("consumedCalories", 476)
                .containsEntry("remainingCalories", 1624)
                .containsEntry("proteinConsumedGrams", new BigDecimal("31.2"));
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
    void keepsTheCalorieGoalWhileAWeightGoalHoldsTheCurrentGoalSlot() {
        RecommendationContextEntity context = baseContext();
        context.setLatestWeightKg(new BigDecimal("82.50"));
        context.setEnergyTargetCalories(2812);
        context.setCurrentGoalType("WEIGHT");
        context.setCurrentGoalTargetValue(new BigDecimal("75"));
        context.setCalorieGoalKcal(2100);

        RuleOutcome outcome = rule.evaluate(context).orElseThrow();

        assertThat(outcome.facts())
                .containsEntry("basis", "CALORIE_GOAL")
                .containsEntry("targetCalories", 2100);
    }

    @Test
    void prefersAnExplicitCalorieGoalOverTheEstimate() {
        RecommendationContextEntity context = baseContext();
        context.setCalorieGoalKcal(2100);

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
