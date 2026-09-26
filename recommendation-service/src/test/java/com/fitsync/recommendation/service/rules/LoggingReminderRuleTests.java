package com.fitsync.recommendation.service.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.junit.jupiter.api.Test;

class LoggingReminderRuleTests {

    // Fixed "today": 2026-07-06 (UTC).
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-06T12:00:00Z"), ZoneOffset.UTC);
    private final LoggingReminderRule rule = new LoggingReminderRule(clock);

    @Test
    void nudgesAfterThreeQuietDays() {
        RuleOutcome outcome = rule.evaluate(context("2026-07-03")).orElseThrow();

        assertThat(outcome.type()).isEqualTo(RecommendationTypes.LOGGING_REMINDER);
        assertThat(outcome.facts())
                .containsEntry("daysSinceLastMealLog", 3)
                .containsEntry("lastMealLogOn", LocalDate.parse("2026-07-03"));
    }

    @Test
    void staysSilentForNormalGaps() {
        assertThat(rule.evaluate(context("2026-07-04"))).isEmpty();
        assertThat(rule.evaluate(context("2026-07-06"))).isEmpty();
    }

    @Test
    void neverNagsSomeoneWhoHasNotLoggedYet() {
        assertThat(rule.evaluate(new RecommendationContextEntity())).isEmpty();
    }

    private static RecommendationContextEntity context(String lastMealDate) {
        RecommendationContextEntity context = new RecommendationContextEntity();
        context.setUserId(UUID.randomUUID());
        context.setTodayDate(LocalDate.parse(lastMealDate));
        return context;
    }
}
