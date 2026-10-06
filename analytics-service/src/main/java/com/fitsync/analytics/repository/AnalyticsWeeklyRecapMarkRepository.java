package com.fitsync.analytics.repository;

import java.time.LocalDate;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsWeeklyRecapMarkEntity;
import com.fitsync.analytics.entity.AnalyticsWeeklyRecapMarkKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsWeeklyRecapMarkRepository
        extends JpaRepository<AnalyticsWeeklyRecapMarkEntity, AnalyticsWeeklyRecapMarkKey> {

    boolean existsByUserIdAndWeekStart(UUID userId, LocalDate weekStart);

    void deleteByUserId(UUID userId);
}
