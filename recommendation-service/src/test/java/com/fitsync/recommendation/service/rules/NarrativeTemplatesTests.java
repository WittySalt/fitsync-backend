package com.fitsync.recommendation.service.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class NarrativeTemplatesTests {

    private final NarrativeTemplates templates = new NarrativeTemplates();

    @Test
    void nutritionGapVariants() {
        assertThat(render(RecommendationTypes.NUTRITION_GAP, Map.of(
                "targetCalories", 2184, "consumedCalories", 1050, "remainingCalories", 1134,
                "proteinTargetGrams", new BigDecimal("132.0"),
                "proteinRemainingGrams", new BigDecimal("66.50"))))
                .isEqualTo("You have 1134 kcal left today, and you're 66.50g short of your 132.0g protein target"
                        + " — favor a protein-rich meal.");

        assertThat(render(RecommendationTypes.NUTRITION_GAP, Map.of(
                "targetCalories", 2184, "consumedCalories", 1050, "remainingCalories", 1134)))
                .isEqualTo("You have 1134 kcal left of today's 2184 kcal target.");

        assertThat(render(RecommendationTypes.NUTRITION_GAP, Map.of(
                "targetCalories", 2184, "consumedCalories", 2400, "remainingCalories", -216)))
                .isEqualTo("You've hit today's 2184 kcal budget (216 kcal over)."
                        + " Lighter choices for the rest of the day.");
    }

    @Test
    void personalRecordVariants() {
        assertThat(render(RecommendationTypes.PERSONAL_RECORD, Map.of(
                "exercise", "Barbell Squat", "recordType", "MAX_WEIGHT_KG",
                "value", new BigDecimal("120.00"), "daysAgo", 1)))
                .isEqualTo("New personal record: heaviest weight on Barbell Squat (120)"
                        + " — that's the payoff of consistent work.");

        assertThat(render(RecommendationTypes.PERSONAL_RECORD, Map.of(
                "recordType", "BEST_SESSION_VOLUME_KG", "daysAgo", 0)))
                .isEqualTo("New personal record: best session volume — that's the payoff of consistent work.");
    }

    @Test
    void loggingReminderTemplate() {
        assertThat(render(RecommendationTypes.LOGGING_REMINDER, Map.of(
                "daysSinceLastMealLog", 4, "lastMealLogOn", "2026-07-02")))
                .isEqualTo("It's been 4 days since your last logged meal"
                        + " — one quick log today gets the picture back.");
    }

    @Test
    void weeklyAdherenceVariants() {
        assertThat(render(RecommendationTypes.WEEKLY_ADHERENCE, Map.of(
                "targetDaysPerWeek", 3, "activeDays", 5, "shortfallDays", 0, "status", "MET")))
                .isEqualTo("Last week's goal met: 5 of 3 training days done — great consistency.");

        assertThat(render(RecommendationTypes.WEEKLY_ADHERENCE, Map.of(
                "targetDaysPerWeek", 4, "activeDays", 2, "shortfallDays", 2, "status", "MISSED")))
                .isEqualTo("You trained 2 of 4 planned days last week — 2 more gets you to target this week.");
    }

    @Test
    void goalPaceVariants() {
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("targetWeightKg", new BigDecimal("80"));
        base.put("targetDate", LocalDate.parse("2026-10-01"));
        base.put("distanceKg", new BigDecimal("7.2"));
        base.put("projectedDate", LocalDate.parse("2026-09-16"));

        base.put("status", "AHEAD");
        base.put("daysEarly", 15L);
        assertThat(render(RecommendationTypes.GOAL_PACE, base))
                .isEqualTo("7.2 kg to 80 kg. On course for 16 Sept, 15 days early.");

        base.put("status", "ON_TRACK");
        assertThat(render(RecommendationTypes.GOAL_PACE, base))
                .isEqualTo("7.2 kg to 80 kg. On course for 16 Sept.");

        base.put("status", "BEHIND");
        base.put("projectedDate", LocalDate.parse("2026-10-26"));
        base.put("daysLate", 25L);
        assertThat(render(RecommendationTypes.GOAL_PACE, base))
                .isEqualTo("7.2 kg to 80 kg. At this pace 26 Oct, 25 days late.");

        base.remove("projectedDate");
        assertThat(render(RecommendationTypes.GOAL_PACE, base))
                .isEqualTo("7.2 kg to 80 kg. Your date is 1 Oct.");

        base.put("status", "REACHED");
        assertThat(render(RecommendationTypes.GOAL_PACE, base)).isEqualTo("80 kg reached.");
    }

    @Test
    void streakNudgeVariants() {
        assertThat(render(RecommendationTypes.STREAK_NUDGE, Map.of(
                "streakDays", 7, "nextMilestone", 14, "daysToNextMilestone", 7)))
                .isEqualTo("7-day streak! 7 more days to reach the 14-day milestone.");

        assertThat(render(RecommendationTypes.STREAK_NUDGE, Map.of("streakDays", 400)))
                .isEqualTo("400 days straight — you've outgrown every milestone. Keep it rolling.");
    }

    @Test
    void coldStartVariants() {
        assertThat(render(RecommendationTypes.COLD_START, Map.of(
                "goalType", "WEIGHT", "workoutDaysPerWeek", 4)))
                .isEqualTo("Your WEIGHT goal and 4 training days a week are set — log your first workout or meal"
                        + " to unlock personalized insights.");

        assertThat(render(RecommendationTypes.COLD_START, Map.of("focusAreas", List.of("CHEST", "LEGS"))))
                .isEqualTo("Ready when you are: a first chest session would kick off your"
                        + " personalized recommendations.");

        assertThat(render(RecommendationTypes.COLD_START, Map.of("dietaryPreference", "VEGETARIAN")))
                .isEqualTo("Log your first workout or meal to unlock personalized recommendations.");
    }

    @Test
    void unknownTypeIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> templates.render(new RuleOutcome("SOMETHING_ELSE", Map.of())));
    }

    private String render(String type, Map<String, Object> facts) {
        return templates.render(new RuleOutcome(type, facts));
    }
}
