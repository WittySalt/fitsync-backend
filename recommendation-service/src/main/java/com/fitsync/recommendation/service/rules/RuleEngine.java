package com.fitsync.recommendation.service.rules;

import java.util.List;
import java.util.Optional;

import com.fitsync.recommendation.domain.TodayIntake;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.springframework.stereotype.Service;

/**
 * Registry over all {@link Rule} beans. {@link #evaluateAll} runs every rule against a
 * context and collects the outcomes that apply — invoked by the resolver funnel (RC-4)
 * on refresh, never per Kafka event.
 */
@Service
public class RuleEngine {

    private final List<Rule> rules;

    public RuleEngine(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public List<RuleOutcome> evaluateAll(RecommendationContextEntity context) {
        return evaluateAll(context, TodayIntake.unknown());
    }

    /** Every rule, with today's intake as the diary shows it (read once by the caller). */
    public List<RuleOutcome> evaluateAll(RecommendationContextEntity context, TodayIntake today) {
        TodayIntake intake = today == null ? TodayIntake.unknown() : today;
        return rules.stream()
                .map(rule -> rule.evaluate(context, intake))
                .flatMap(Optional::stream)
                .toList();
    }
}
