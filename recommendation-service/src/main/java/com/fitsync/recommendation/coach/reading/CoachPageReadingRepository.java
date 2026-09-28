package com.fitsync.recommendation.coach.reading;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CoachPageReadingRepository extends JpaRepository<CoachPageReadingEntity, UUID> {

    /** At most one row per user and surface, which the unique constraint also enforces. */
    Optional<CoachPageReadingEntity> findByUserIdAndSurface(UUID userId, ReadingSurface surface);

    void deleteByUserId(UUID userId);
}
