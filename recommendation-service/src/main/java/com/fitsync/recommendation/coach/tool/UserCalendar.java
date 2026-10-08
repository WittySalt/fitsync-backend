package com.fitsync.recommendation.coach.tool;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
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

    /**
     * The zone a turn speaks in: the one the client reported with the request, else the stored
     * profile zone, else UTC. Every coach tool that turns an instant into a day uses this one, so a
     * session at 06:45 in Singapore is dated the day the user lived it, not the UTC day before.
     */
    public ZoneId zone(CoachCallContext context) {
        String reported = context.timezone();
        if (reported != null && !reported.isBlank()) {
            try {
                return ZoneId.of(reported.strip());
            } catch (DateTimeException ex) {
                // An unknown zone name falls through to the stored one.
            }
        }
        return zone(context.userId());
    }

    /** The zone as the other services expect it in the zone header: UTC is written "UTC", not "Z". */
    public static String zoneName(ZoneId zone) {
        return "Z".equals(zone.getId()) ? "UTC" : zone.getId();
    }

    /**
     * The local day of an instant another service sent, such as a session's {@code completedAt}
     * ("2026-10-07T22:45:00Z", or with an offset). Empty when the text is not an instant. Never cut
     * the first ten characters off such a text: that is the UTC day, which for a morning east of
     * Greenwich is the day before.
     */
    public static Optional<LocalDate> localDate(String instant, ZoneId zone) {
        if (instant == null || instant.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(instant.strip()).atZoneSameInstant(zone).toLocalDate());
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    public LocalDate today(ZoneId zone) {
        return LocalDate.now(clock.withZone(zone));
    }
}
