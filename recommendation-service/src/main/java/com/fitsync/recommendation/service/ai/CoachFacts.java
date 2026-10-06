package com.fitsync.recommendation.service.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.domain.FoodGroupCalories;
import com.fitsync.recommendation.domain.TodayIntake;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import com.fitsync.recommendation.service.rules.NutritionGapRule;
import com.fitsync.recommendation.service.rules.RecommendationTypes;
import com.fitsync.recommendation.service.rules.RuleEngine;
import com.fitsync.recommendation.service.rules.RuleOutcome;
import org.springframework.stereotype.Service;

/**
 * The one facts assembler behind every AI surface (A1 weekly narrative, A4 coach Q&A):
 * compute-in-code, model-phrases. Facts are grouped semantically (training /
 * nutritionToday / goalPace / personalRecord / foodGroupWeek) and carry
 * the rules' own status words, so the model has nothing left to judge.
 */
@Service
public class CoachFacts {

    private final RuleEngine ruleEngine;
    private final ObjectMapper objectMapper;

    public CoachFacts(RuleEngine ruleEngine, ObjectMapper objectMapper) {
        this.ruleEngine = ruleEngine;
        this.objectMapper = objectMapper;
    }

    public static final String TRAINING_PERIOD = "LAST_FULL_WEEK";

    /** @param previousNarrative last narrative for week-over-week continuity; nullable */
    public Map<String, Object> assemble(RecommendationContextEntity context, String previousNarrative) {
        return assemble(context, previousNarrative, TodayIntake.unknown());
    }

    /**
     * @param previousNarrative last narrative for week-over-week continuity; nullable
     * @param today             today's intake as the Nutrition Diary shows it, read once by the caller
     */
    public Map<String, Object> assemble(RecommendationContextEntity context, String previousNarrative,
                                        TodayIntake today) {
        Map<String, RuleOutcome> outcomes = ruleEngine.evaluateAll(context, today).stream()
                .collect(Collectors.toMap(RuleOutcome::type, Function.identity()));

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("weekStart", context.getRecentWeekStart());
        if (context.getCurrentGoalType() != null) {
            facts.put("goalType", context.getCurrentGoalType());
        }
        facts.put("training", trainingFacts(context, outcomes.get(RecommendationTypes.WEEKLY_ADHERENCE)));
        ruleFacts(outcomes, RecommendationTypes.NUTRITION_GAP).ifPresent(gap -> {
            gap.put("minimumDailyCalories", NutritionGapRule.MIN_DAILY_CALORIES);
            facts.put("nutritionToday", gap);
        });
        ruleFacts(outcomes, RecommendationTypes.GOAL_PACE)
                .ifPresent(pace -> facts.put("goalPace", pace));
        ruleFacts(outcomes, RecommendationTypes.PERSONAL_RECORD)
                .ifPresent(record -> facts.put("personalRecord", record));
        foodGroupWeekFacts(context).ifPresent(groups -> facts.put("foodGroupWeek", groups));
        if (context.getFocusAreas() != null) {
            facts.put("focusAreas", context.getFocusAreas());
        }
        if (previousNarrative != null && !previousNarrative.isBlank()) {
            facts.put("previousNarrative", previousNarrative);
        }
        return facts;
    }

    private static Map<String, Object> trainingFacts(RecommendationContextEntity context, RuleOutcome adherence) {
        Map<String, Object> training = new LinkedHashMap<>();
        // The recent week is the one the weekly recap covered, which is the week before the current
        // one. Named here because a model given only counts takes them for the current week.
        training.put("period", TRAINING_PERIOD);
        if (adherence != null) {
            training.putAll(adherence.facts());
            training.remove("weekStart");
        } else {
            training.put("workoutCount", context.getRecentWorkoutCount());
            training.put("activeDays", context.getRecentActiveDays());
        }
        training.put("totalMinutes", context.getRecentWorkoutMinutes());
        return training;
    }

    private static Optional<Map<String, Object>> ruleFacts(Map<String, RuleOutcome> outcomes, String type) {
        return Optional.ofNullable(outcomes.get(type))
                .map(outcome -> new LinkedHashMap<>(outcome.facts()));
    }

    /**
     * The week's food-group balance with shares pre-computed (largest first) — the
     * model phrases "meat-heavy / veg-light", it never does the arithmetic.
     */
    private Optional<Map<String, Object>> foodGroupWeekFacts(RecommendationContextEntity context) {
        Map<String, Integer> groups = FoodGroupCalories.read(
                objectMapper, context.getWeekFoodGroupCaloriesJson());
        int totalKcal = groups.values().stream().filter(Objects::nonNull)
                .mapToInt(Integer::intValue).sum();
        if (totalKcal <= 0) {
            return Optional.empty();
        }
        Map<String, Integer> sharePctByGroup = groups.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Math.round(entry.getValue() * 100f / totalKcal),
                        (a, b) -> a,
                        LinkedHashMap::new));
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("weekStart", context.getNutritionWeekStart());
        facts.put("classifiedKcal", totalKcal);
        facts.put("sharePctByGroup", sharePctByGroup);
        return Optional.of(facts);
    }
}
