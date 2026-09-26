package com.fitsync.recommendation.service.rules;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.springframework.stereotype.Service;

/**
 * R4 — consistency nudge from the analytics streak signal: current streak length plus
 * the next milestone on the ladder (matching the milestones analytics celebrates).
 */
@Service
public class StreakNudgeRule implements Rule {

    static final List<Integer> MILESTONE_LADDER = List.of(7, 14, 30, 60, 100, 365);

    @Override
    public String type() {
        return RecommendationTypes.STREAK_NUDGE;
    }

    @Override
    public Optional<RuleOutcome> evaluate(RecommendationContextEntity context) {
        Integer streakDays = context.getCurrentStreakDays();
        if (streakDays == null || streakDays <= 0) {
            return Optional.empty();
        }

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("streakDays", streakDays);
        MILESTONE_LADDER.stream()
                .filter(milestone -> milestone.intValue() > streakDays.intValue())
                .findFirst()
                .ifPresent(nextMilestone -> {
                    facts.put("nextMilestone", nextMilestone);
                    facts.put("daysToNextMilestone", nextMilestone.intValue() - streakDays.intValue());
                });
        return Optional.of(new RuleOutcome(type(), facts));
    }
}
