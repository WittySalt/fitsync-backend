package com.fitsync.analytics.repository;

import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalyticsProcessedEventRepository extends JpaRepository<AnalyticsProcessedEventEntity, UUID> {

    /**
     * Detaches a deleted account's rows from the user: the event ids stay, so a late redelivery of an event
     * already applied is still skipped, but nothing ties them to the person any more. Returns the rows changed.
     */
    @Modifying
    @Query("update AnalyticsProcessedEventEntity e set e.userId = null where e.userId = :userId")
    int anonymiseByUserId(@Param("userId") UUID userId);
}
