package com.fitsync.recommendation.service.rules;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.fitsync.recommendation.domain.UserTimeZones;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import org.springframework.stereotype.Service;

/**
 * R8 — logging reminder: a gentle nudge when meal logging has gone quiet. Fires only
 * for users who HAVE logged before (a user with no data at all is R5 cold-start
 * territory) and stays silent for the first two quiet days — missing one day is
 * normal life, three is a lapse worth a nudge.
 */
@Service
public class LoggingReminderRule implements Rule {

    static final int QUIET_DAYS_THRESHOLD = 3;

    private final Clock clock;

    public LoggingReminderRule(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String type() {
        return RecommendationTypes.LOGGING_REMINDER;
    }

    @Override
    public Optional<RuleOutcome> evaluate(RecommendationContextEntity context) {
        if (context.getTodayDate() == null) {
            return Optional.empty();
        }
        LocalDate today = LocalDate.now(clock.withZone(UserTimeZones.resolve(context.getTimezone())));
        long quietDays = ChronoUnit.DAYS.between(context.getTodayDate(), today);
        if (quietDays < QUIET_DAYS_THRESHOLD) {
            return Optional.empty();
        }

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("daysSinceLastMealLog", (int) quietDays);
        facts.put("lastMealLogOn", context.getTodayDate());
        return Optional.of(new RuleOutcome(type(), facts));
    }
}
