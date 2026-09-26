package com.fitsync.recommendation.service.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.junit.jupiter.api.Test;

class RuleEngineTests {

    @Test
    void collectsOnlyTheOutcomesOfRulesThatApply() {
        Rule fires = rule("FIRES", Optional.of(new RuleOutcome("FIRES", Map.of("value", 1))));
        Rule quiet = rule("QUIET", Optional.empty());
        RuleEngine engine = new RuleEngine(List.of(fires, quiet));

        RecommendationContextEntity context = new RecommendationContextEntity();
        context.setUserId(UUID.randomUUID());

        List<RuleOutcome> outcomes = engine.evaluateAll(context);

        assertThat(outcomes).hasSize(1);
        assertThat(outcomes.get(0).type()).isEqualTo("FIRES");
    }

    private static Rule rule(String type, Optional<RuleOutcome> result) {
        return new Rule() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public Optional<RuleOutcome> evaluate(RecommendationContextEntity context) {
                return result;
            }
        };
    }
}
