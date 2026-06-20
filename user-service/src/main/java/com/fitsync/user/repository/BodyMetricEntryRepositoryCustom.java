package com.fitsync.user.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.user.entity.BodyMetricEntryEntity;
import org.springframework.data.domain.Pageable;

public interface BodyMetricEntryRepositoryCustom {

    List<BodyMetricEntryEntity> findHistory(UUID userId, Instant from, Instant to, Pageable pageable);

    List<BodyMetricEntryEntity> findTrendSource(UUID userId, Instant from, Instant to);
}
