package com.fitsync.user.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.user.entity.BodyMetricEntryEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BodyMetricEntryRepository extends JpaRepository<BodyMetricEntryEntity, UUID> {

    Optional<BodyMetricEntryEntity> findTopByUserIdOrderByMeasuredAtDesc(UUID userId);

    Optional<BodyMetricEntryEntity> findTopByUserIdAndNoteOrderByMeasuredAtDesc(UUID userId, String note);

    Optional<BodyMetricEntryEntity> findByMetricIdAndUserId(UUID metricId, UUID userId);

    List<BodyMetricEntryEntity> findByUserIdOrderByMeasuredAtDesc(UUID userId);

    @Query("""
            select metric from BodyMetricEntryEntity metric
            where metric.userId = :userId
              and (:from is null or metric.measuredAt >= :from)
              and (:to is null or metric.measuredAt <= :to)
            order by metric.measuredAt desc
            """)
    List<BodyMetricEntryEntity> findHistory(
            @Param("userId") UUID userId,
            @Param("from") java.time.Instant from,
            @Param("to") java.time.Instant to,
            Pageable pageable);

    @Query("""
            select metric from BodyMetricEntryEntity metric
            where metric.userId = :userId
              and (:from is null or metric.measuredAt >= :from)
              and (:to is null or metric.measuredAt <= :to)
            order by metric.measuredAt asc
            """)
    List<BodyMetricEntryEntity> findTrendSource(
            @Param("userId") UUID userId,
            @Param("from") java.time.Instant from,
            @Param("to") java.time.Instant to);
}
