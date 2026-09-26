package com.fitsync.recommendation.coach.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The calorie floor, which is the one answer the coach must get right every time.
 *
 * <p>Both directions matter. A floor that lets "800 kcal a day" through is the failure the rule
 * exists to prevent; a floor that refuses "is 800 kcal a lot for a burger" makes the coach
 * useless and gets it switched off. The cases here are the boundary between the two.
 */
class CoachGuardrailTests {

    private final CoachGuardrail guardrail = new CoachGuardrail();

    @Test
    @DisplayName("a plan to eat below the floor is refused, with the floor and the reason")
    void refusesAPlanBelowTheFloor() {
        for (String message : new String[] {
                "Should I cut to 800 kcal a day?",
                "I want to eat only 1000 calories daily until November",
                "can I drop to 900cal per day to lose faster",
                "Plan me a 1100 kcal diet",
                "Is a 700 kcal deficit fast, like eating 500 cals a day, okay?"
        }) {
            assertThat(guardrail.check(message))
                    .as(message)
                    .hasValueSatisfying(refusal -> {
                        assertThat(refusal.rule()).isEqualTo("CALORIE_FLOOR");
                        assertThat(refusal.message()).contains("1200 kcal");
                        assertThat(refusal.message()).doesNotContain("sorry");
                    });
        }
    }

    @Test
    @DisplayName("a figure at or above the floor is not refused")
    void allowsTheFloorAndAbove() {
        assertThat(guardrail.check("Can I eat 1200 kcal a day?")).isEmpty();
        assertThat(guardrail.check("I'm planning 1500 kcal daily")).isEmpty();
        assertThat(guardrail.check("Should I cut to 1800 calories per day?")).isEmpty();
    }

    @Test
    @DisplayName("a small number that is not a daily intake is not refused")
    void allowsFiguresThatAreNotAnIntakePlan() {
        for (String message : new String[] {
                "How many calories did I burn on the treadmill?",
                "Is 800 kcal a lot for one burger?",
                "My lunch was 600 kcal, is that fine?",
                "What is 300 kcal of chicken in grams?",
                "I have 63 kcal left today, what fits?",
                // Found by the evaluation harness: refused on its first run, because "eat" read as intent.
                "What should I eat tonight with 63 kcal left?",
                "I burned 400 kcal on the bike, can I eat more?"
        }) {
            assertThat(guardrail.check(message)).as(message).isEmpty();
        }
    }

    @Test
    @DisplayName("ordinary questions with no calorie figure pass straight through")
    void allowsOrdinaryQuestions() {
        assertThat(guardrail.check("Should I train today?")).isEmpty();
        assertThat(guardrail.check("Build me a session for today")).isEmpty();
        assertThat(guardrail.check("")).isEmpty();
        assertThat(guardrail.check(null)).isEmpty();
    }

    @Test
    @DisplayName("the floor is the same number the nutrition gap rule uses")
    void sharesTheFloorWithTheRuleEngine() {
        assertThat(CoachGuardrail.CALORIE_FLOOR).isEqualTo(1200);
    }
}
