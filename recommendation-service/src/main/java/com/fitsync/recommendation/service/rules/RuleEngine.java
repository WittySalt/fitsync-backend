package com.fitsync.recommendation.service.rules;

import java.util.List;
import java.util.Optional;

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
        return rules.stream()
                .map(rule -> rule.evaluate(context))
                .flatMap(Optional::stream)
                .toList();
    }
}
