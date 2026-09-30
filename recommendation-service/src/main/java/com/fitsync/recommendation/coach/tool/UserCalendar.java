package com.fitsync.recommendation.coach.tool;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import com.fitsync.recommendation.domain.UserTimeZones;
import com.fitsync.recommendation.entity.RecommendationContextEntity;
import com.fitsync.recommendation.repository.RecommendationContextRepository;
import org.springframework.stereotype.Component;

/**
 * The user's own calendar: their timezone and today's date in it.
 *
 * <p>The timezone is the one the projection holds for the user, the same one the computed facts use,
 * so a day the coach talks about is the day the rest of the app means. A user with no projected
 * timezone gets UTC, which is what the rules do as well.
 */
@Component
public class UserCalendar {

    private final RecommendationContextRepository contextRepository;
    private final Clock clock;

    public UserCalendar(RecommendationContextRepository contextRepository, Clock clock) {
        this.contextRepository = contextRepository;
        this.clock = clock;
    }

    public ZoneId zone(UUID userId) {
        return UserTimeZones.resolve(contextRepository.findById(userId)
                .map(RecommendationContextEntity::getTimezone)
                .orElse(null));
    }

    public LocalDate today(ZoneId zone) {
        return LocalDate.now(clock.withZone(zone));
    }
}
