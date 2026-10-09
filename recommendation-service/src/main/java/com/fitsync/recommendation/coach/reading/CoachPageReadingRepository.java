package com.fitsync.recommendation.coach.reading;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CoachPageReadingRepository extends JpaRepository<CoachPageReadingEntity, UUID> {

    /**
     * The row of a page reading. Only for the surfaces that keep one row per user, which every page
     * does; weekly reviews keep one per week and are read with the period key.
     */
    Optional<CoachPageReadingEntity> findByUserIdAndSurface(UUID userId, ReadingSurface surface);

    /** At most one row per user, surface and period, which the unique constraint also enforces. */
    Optional<CoachPageReadingEntity> findByUserIdAndSurfaceAndPeriodKey(UUID userId, ReadingSurface surface,
                                                                        String periodKey);

    /** The rows of several periods at once, which is how the weekly reviews are read. */
    List<CoachPageReadingEntity> findByUserIdAndSurfaceAndPeriodKeyIn(UUID userId, ReadingSurface surface,
                                                                      Collection<String> periodKeys);

    /** Every reading a user has, for the account data export, in a stable order. */
    List<CoachPageReadingEntity> findByUserIdOrderBySurfaceAscPeriodKeyAsc(UUID userId);

    void deleteByUserId(UUID userId);

    /**
     * Reading outcomes for the admin quality report, inside one window, keyed by
     * {@code dateTimeUpdated} since that is when a row was last actually written. A constructor
     * projection rather than the entity, so {@code headline}, {@code body} and {@code citations} —
     * which can quote a user's own numbers back to them — are never loaded.
     */
    @Query("select new com.fitsync.recommendation.coach.reading.CoachPageReadingQualityRow("
            + "r.surface, r.source, r.outcome) "
            + "from CoachPageReadingEntity r "
            + "where r.dateTimeUpdated >= :start and r.dateTimeUpdated < :end")
    List<CoachPageReadingQualityRow> findQualityRows(@Param("start") Instant start, @Param("end") Instant end);
}
