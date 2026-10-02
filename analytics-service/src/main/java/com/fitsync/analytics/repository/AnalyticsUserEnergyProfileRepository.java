package com.fitsync.analytics.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserEnergyProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsUserEnergyProfileRepository
        extends JpaRepository<AnalyticsUserEnergyProfileEntity, UUID> {

    /** The newest computation strictly before the given instant (the start of the next day). */
    Optional<AnalyticsUserEnergyProfileEntity> findFirstByUserIdAndComputedAtBeforeOrderByComputedAtDesc(
            UUID userId, Instant computedAt);

    /** The earliest computation, used for days that lie before every computation. */
    Optional<AnalyticsUserEnergyProfileEntity> findFirstByUserIdOrderByComputedAtAsc(UUID userId);

    /** Computations inside the half-open window [from, to), oldest first. */
    List<AnalyticsUserEnergyProfileEntity> findByUserIdAndComputedAtGreaterThanEqualAndComputedAtBeforeOrderByComputedAtAsc(
            UUID userId, Instant from, Instant to);

    void deleteByUserId(UUID userId);
}
