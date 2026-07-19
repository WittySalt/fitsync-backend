package com.fitsync.recommendation.service.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.junit.jupiter.api.Test;

class PersonalRecordRuleTests {

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-06T12:00:00Z"), ZoneOffset.UTC);
    private final PersonalRecordRule rule = new PersonalRecordRule(clock);

    @Test
    void celebratesAFreshRecord() {
        RuleOutcome outcome = rule.evaluate(context(Instant.parse("2026-07-04T18:00:00Z"))).orElseThrow();

        assertThat(outcome.type()).isEqualTo(RecommendationTypes.PERSONAL_RECORD);
        assertThat(outcome.facts())
                .containsEntry("exercise", "Barbell Squat")
                .containsEntry("recordType", "MAX_WEIGHT_KG")
                .containsEntry("value", new BigDecimal("120.00"))
                .containsEntry("daysAgo", 1);
    }

    @Test
    void goesQuietOnceTheRecordIsOlderThanAWeek() {
        assertThat(rule.evaluate(context(Instant.parse("2026-06-28T11:00:00Z")))).isEmpty();
    }

    @Test
    void staysSilentWithoutARecord() {
        assertThat(rule.evaluate(new RecommendationContextEntity())).isEmpty();
    }

    private static RecommendationContextEntity context(Instant achievedAt) {
        RecommendationContextEntity context = new RecommendationContextEntity();
        context.setUserId(UUID.randomUUID());
        context.setLastPrExercise("Barbell Squat");
        context.setLastPrRecordType("MAX_WEIGHT_KG");
        context.setLastPrValue(new BigDecimal("120.00"));
        context.setLastPrAt(achievedAt);
        return context;
    }
}
