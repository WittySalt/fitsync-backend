package com.fitsync.analytics.repository;

import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsProcessedEventRepository extends JpaRepository<AnalyticsProcessedEventEntity, UUID> {

    void deleteByUserId(UUID userId);
}
