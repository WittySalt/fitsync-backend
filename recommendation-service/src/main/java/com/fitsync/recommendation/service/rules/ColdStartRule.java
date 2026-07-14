package com.fitsync.recommendation.service.rules;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.springframework.stereotype.Service;

/**
 * R5 — cold-start defaults: fires only while the user has no activity signal yet
 * (no recap, no streak, no logged nutrition) but has declared a goal or preferences,
 * surfacing those inputs so the template tier can phrase a getting-started suggestion.
 * Goes quiet as soon as real activity data arrives — the other rules take over.
 */
@Service
public class ColdStartRule implements Rule {

    @Override
    public String type() {
        return RecommendationTypes.COLD_START;
    }

    @Override
    public Optional<RuleOutcome> evaluate(RecommendationContextEntity context) {
        if (hasActivitySignal(context)) {
            return Optional.empty();
        }

        Map<String, Object> facts = new LinkedHashMap<>();
        if (context.getCurrentGoalType() != null) {
            facts.put("goalType", context.getCurrentGoalType());
        }
        if (context.getWorkoutDaysPerWeek() != null) {
            facts.put("workoutDaysPerWeek", context.getWorkoutDaysPerWeek());
        }
        if (context.getFocusAreas() != null) {
            facts.put("focusAreas", splitCsv(context.getFocusAreas()));
        }
        if (context.getEquipmentInventory() != null) {
            facts.put("equipment", splitCsv(context.getEquipmentInventory()));
        }
        if (context.getDietaryPreference() != null) {
            facts.put("dietaryPreference", context.getDietaryPreference());
        }
        if (facts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RuleOutcome(type(), facts));
    }

    private static boolean hasActivitySignal(RecommendationContextEntity context) {
        return context.getRecentWorkoutCount() != null
                || context.getRecentActiveDays() != null
                || context.getCurrentStreakDays() != null
                || context.getTodayCaloriesConsumed() != null;
    }

    private static List<String> splitCsv(String csv) {
        return List.of(csv.split(","));
    }
}
