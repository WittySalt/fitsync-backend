package com.fitsync.recommendation.service.rules;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.springframework.stereotype.Service;

/**
 * R6 — PR celebration: surfaces the latest personal record while it is fresh (within
 * one week). Purely a read of the context's PR snapshot — the detection itself
 * happened in workout-service.
 */
@Service
public class PersonalRecordRule implements Rule {

    static final Duration CELEBRATION_WINDOW = Duration.ofDays(7);

    private final Clock clock;

    public PersonalRecordRule(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String type() {
        return RecommendationTypes.PERSONAL_RECORD;
    }

    @Override
    public Optional<RuleOutcome> evaluate(RecommendationContextEntity context) {
        if (context.getLastPrAt() == null || context.getLastPrRecordType() == null) {
            return Optional.empty();
        }
        Instant now = Instant.now(clock);
        Duration age = Duration.between(context.getLastPrAt(), now);
        if (age.isNegative() || age.compareTo(CELEBRATION_WINDOW) > 0) {
            return Optional.empty();
        }

        Map<String, Object> facts = new LinkedHashMap<>();
        if (context.getLastPrExercise() != null) {
            facts.put("exercise", context.getLastPrExercise());
        }
        facts.put("recordType", context.getLastPrRecordType());
        if (context.getLastPrValue() != null) {
            facts.put("value", context.getLastPrValue());
        }
        facts.put("daysAgo", (int) age.toDays());
        return Optional.of(new RuleOutcome(type(), facts));
    }
}
