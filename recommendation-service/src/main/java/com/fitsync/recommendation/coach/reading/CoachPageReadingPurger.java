package com.fitsync.recommendation.coach.reading;

import java.util.UUID;

import com.fitsync.recommendation.handler.AccountDataPurger;
import org.springframework.stereotype.Component;

/**
 * Removes a deleted user's cached page readings.
 *
 * <p>Implements {@code AccountDataPurger} rather than being called directly from
 * {@code AccountDeletedEventHandler}, because that handler lives in the projection and must never
 * import the coach package (see {@code CoachDependencyDirectionTests}). This class is the coach
 * side of that boundary: it depends on the projection's interface, which is the direction the
 * architecture allows.
 */
@Component
class CoachPageReadingPurger implements AccountDataPurger {

    private final CoachPageReadingRepository repository;

    CoachPageReadingPurger(CoachPageReadingRepository repository) {
        this.repository = repository;
    }

    @Override
    public void purgeForUser(UUID userId) {
        repository.deleteByUserId(userId);
    }
}
