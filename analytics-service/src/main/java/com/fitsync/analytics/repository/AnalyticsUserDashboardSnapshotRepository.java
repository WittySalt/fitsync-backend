package com.fitsync.analytics.repository;

import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsUserDashboardSnapshotRepository
        extends JpaRepository<AnalyticsUserDashboardSnapshotEntity, UUID> {
}
