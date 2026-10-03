package com.fitsync.workout.service;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;

import com.fitsync.workout.repository.UserTimezoneRepository;
import org.springframework.stereotype.Component;

/**
 * Chooses the zone an insight is computed in: the one the request names, else the zone kept in the
 * person's profile, else UTC. A request that names a zone it cannot use is rejected, while a stored
 * zone that cannot be used is skipped, since the person did not send it.
 */
@Component
public class UserZoneResolver {

    /** Named "UTC" rather than "Z", so a response keeps reporting the zone as it did when UTC was the request default. */
    private static final ZoneId UTC = ZoneId.of("UTC");

    private final UserTimezoneRepository userTimezoneRepository;

    public UserZoneResolver(UserTimezoneRepository userTimezoneRepository) {
        this.userTimezoneRepository = userTimezoneRepository;
    }

    public ZoneId resolve(UUID userId, String requestedTimezone) {
        if (requestedTimezone != null && !requestedTimezone.isBlank()) {
            return InsightDates.resolveZone(requestedTimezone);
        }
        return userTimezoneRepository.findById(userId)
                .map(row -> storedZone(row.getTimezone()))
                .orElse(UTC);
    }

    private static ZoneId storedZone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            return UTC;
        }
    }
}
