package com.fitsync.recommendation.service.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.junit.jupiter.api.Test;

class ColdStartRuleTests {

    private final ColdStartRule rule = new ColdStartRule();

    @Test
    void surfacesGoalAndPreferencesForAColdUser() {
        RecommendationContextEntity context = context();
        context.setCurrentGoalType("WEIGHT");
        context.setWorkoutDaysPerWeek(4);
        context.setFocusAreas("CHEST,LEGS");
        context.setEquipmentInventory("DUMBBELLS");
        context.setDietaryPreference("VEGETARIAN");

        RuleOutcome outcome = rule.evaluate(context).orElseThrow();

        assertThat(outcome.facts())
                .containsEntry("goalType", "WEIGHT")
                .containsEntry("workoutDaysPerWeek", 4)
                .containsEntry("focusAreas", List.of("CHEST", "LEGS"))
                .containsEntry("equipment", List.of("DUMBBELLS"))
                .containsEntry("dietaryPreference", "VEGETARIAN");
    }

    @Test
    void goesQuietOnceActivityDataExists() {
        RecommendationContextEntity context = context();
        context.setCurrentGoalType("WEIGHT");
        context.setRecentWorkoutCount(2);

        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void staysQuietWhenThereIsNothingToSuggestFrom() {
        assertThat(rule.evaluate(context())).isEmpty();
    }

    private static RecommendationContextEntity context() {
        RecommendationContextEntity context = new RecommendationContextEntity();
        context.setUserId(UUID.randomUUID());
        return context;
    }
}
