package com.fitsync.recommendation.coach.service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.fitsync.recommendation.coach.config.CoachProperties;
import com.fitsync.recommendation.coach.entity.ArtifactKind;
import com.fitsync.recommendation.coach.entity.CoachTurnRole;
import com.fitsync.recommendation.coach.repository.CoachTurnRepository;
import com.fitsync.recommendation.coach.repository.GeneratedArtifactRepository;
import com.fitsync.recommendation.coach.tool.UserCalendar;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-user limit on orchestrator turns and session drafts, 30 an hour and 150 a day by default.
 *
 * <p>The count is taken from the database rather than from memory: a question the user asked is
 * already a stored user turn, and a draft is a stored artifact, so counting them needs no second
 * source of truth that could drift, survives a restart, and is right across several instances.
 * Only work that actually started is counted, because the check runs before a turn is created and
 * a retried question that finds its existing turn never reaches it.
 *
 * <p>The answer to a refusal says when the next turn is possible: the moment the oldest counted
 * turn of the window that was exceeded ages out, written in the user's own zone.
 */
@Service
public class CoachRateLimiter {

    private static final DateTimeFormatter CLOCK_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final CoachTurnRepository turnRepository;
    private final GeneratedArtifactRepository artifactRepository;
    private final CoachProperties properties;
    private final UserCalendar calendar;
    private final Clock clock;

    public CoachRateLimiter(CoachTurnRepository turnRepository,
                            GeneratedArtifactRepository artifactRepository,
                            CoachProperties properties,
                            UserCalendar calendar,
                            Clock clock) {
        this.turnRepository = turnRepository;
        this.artifactRepository = artifactRepository;
        this.properties = properties;
        this.calendar = calendar;
        this.clock = clock;
    }

    /**
     * @param timezone the zone the client reported, or null to use the user's stored one
     * @throws CoachLimitException when either window is full
     */
    @Transactional(readOnly = true)
    public void check(UUID userId, String timezone) {
        Instant now = Instant.now(clock);
        Instant dayAgo = now.minus(Duration.ofDays(1));
        List<Instant> started = new ArrayList<>(turnRepository.findTurnTimesSince(
                userId, CoachTurnRole.USER, dayAgo));
        started.addAll(artifactRepository.findCreatedTimesSince(userId, ArtifactKind.SESSION_DRAFT, dayAgo));
        Collections.sort(started);

        Instant freeAt = later(
                whenFree(started, now, Duration.ofHours(1), properties.turnsPerHour()),
                whenFree(started, now, Duration.ofDays(1), properties.turnsPerDay()));
        if (freeAt == null) {
            return;
        }

        long retryAfter = Math.max(1, Duration.between(now, freeAt).toSeconds() + 1);
        String at = CLOCK_TIME.format(freeAt.atZone(zoneOf(userId, timezone)));
        String period = retryAfter > Duration.ofHours(1).toSeconds() ? "day" : "hour";
        throw new CoachLimitException(retryAfter,
                "You have asked a lot this " + period + ". You can ask again at " + at + ".");
    }

    /** When a slot frees in one window, or null when the window still has room. */
    private static Instant whenFree(List<Instant> started, Instant now, Duration window, int limit) {
        Instant since = now.minus(window);
        List<Instant> inWindow = started.stream().filter(time -> !time.isBefore(since)).toList();
        if (inWindow.size() < limit) {
            return null;
        }
        // The turn that has to age out is the one that brings the count back under the limit.
        return inWindow.get(inWindow.size() - limit).plus(window);
    }

    private static Instant later(Instant first, Instant second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return second.isAfter(first) ? second : first;
    }

    private ZoneId zoneOf(UUID userId, String timezone) {
        if (timezone != null && !timezone.isBlank()) {
            try {
                return ZoneId.of(timezone);
            } catch (DateTimeException ex) {
                // An unknown zone name falls through to the stored one.
            }
        }
        return calendar.zone(userId);
    }
}
