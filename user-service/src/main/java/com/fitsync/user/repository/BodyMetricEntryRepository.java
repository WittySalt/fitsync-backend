package com.fitsync.user.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.user.entity.BodyMetricEntryEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BodyMetricEntryRepository extends JpaRepository<BodyMetricEntryEntity, UUID>,
        BodyMetricEntryRepositoryCustom {

    Optional<BodyMetricEntryEntity> findTopByUserIdOrderByMeasuredAtDesc(UUID userId);

    Optional<BodyMetricEntryEntity> findTopByUserIdAndWeightKgIsNotNullOrderByMeasuredAtDesc(UUID userId);

    Optional<BodyMetricEntryEntity> findTopByUserIdAndWeightKgIsNotNullAndMeasuredAtLessThanOrderByMeasuredAtDesc(
            UUID userId, Instant before);

    Optional<BodyMetricEntryEntity> findTopByUserIdAndNoteOrderByMeasuredAtDesc(UUID userId, String note);

    Optional<BodyMetricEntryEntity> findByMetricIdAndUserId(UUID metricId, UUID userId);

    List<BodyMetricEntryEntity> findByUserIdOrderByMeasuredAtDesc(UUID userId);

    void deleteByUserId(UUID userId);
}
