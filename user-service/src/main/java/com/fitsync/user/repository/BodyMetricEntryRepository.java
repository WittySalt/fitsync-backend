package com.fitsync.user.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.user.entity.BodyMetricEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BodyMetricEntryRepository extends JpaRepository<BodyMetricEntryEntity, UUID>,
        BodyMetricEntryRepositoryCustom {

    Optional<BodyMetricEntryEntity> findTopByUserIdOrderByMeasuredAtDesc(UUID userId);

    Optional<BodyMetricEntryEntity> findTopByUserIdAndWeightKgIsNotNullOrderByMeasuredAtDesc(UUID userId);

    Optional<BodyMetricEntryEntity> findTopByUserIdAndWeightKgIsNotNullAndMeasuredAtLessThanOrderByMeasuredAtDesc(
            UUID userId, Instant before);

    Optional<BodyMetricEntryEntity> findTopByUserIdAndNoteOrderByMeasuredAtDesc(UUID userId, String note);

    Optional<BodyMetricEntryEntity> findByMetricIdAndUserId(UUID metricId, UUID userId);

    List<BodyMetricEntryEntity> findByUserIdOrderByMeasuredAtDesc(UUID userId);

    /**
     * Erases every entry of the user, including soft-deleted ones, which the entity's restriction would hide from
     * a derived delete. Used only when the whole account is erased.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from body_metric_entries where user_id = :userId", nativeQuery = true)
    int deleteByUserId(@Param("userId") UUID userId);
}
