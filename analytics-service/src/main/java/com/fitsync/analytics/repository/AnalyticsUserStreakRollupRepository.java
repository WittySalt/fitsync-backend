package com.fitsync.analytics.repository;

import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserStreakRollupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsUserStreakRollupRepository
        extends JpaRepository<AnalyticsUserStreakRollupEntity, UUID> {
}
